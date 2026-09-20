# 안드로이드 백그라운드 SMS 자동 포워딩 시스템 설계 및 구현 가이드

## 1. 개요 및 기획 (Plan)

본 문서는 2번 안드로이드 단말에 백그라운드 서비스로 상주하여, 1번 단말로부터 수신된 SMS를 트리거로 감지하고 사전에 정의된 3번 단말로 해당 메시지를 자동 재전송(Relay)하는 독립형 APK의 설계 및 배포 절차를 정리합니다.

### 1.1 메시지 릴레이 흐름
- **1단계 (트리거 감지):** 1번 폰이 2번 폰으로 특정 SMS 발송 → 2번 폰의 OS가 `SMS_RECEIVED` 브로드캐스트 이벤트 발생
- **2단계 (데이터 검증):** 2번 폰 내 앱이 수신 번호(1번) 및 본문 내용을 필터링 및 파싱
- **3단계 (자동 재전송):** `SmsManager` API를 직접 호출하여 3번 폰 번호로 백그라운드 발송 (화면 점등/사용자 개입 없음)

### 1.2 핵심 기술 컴포넌트
- **`BroadcastReceiver`:** 시스템 이벤트(SMS 수신)를 가로채 즉각적인 로직 실행을 유도하는 수신자
- **`Foreground Service`:** 안드로이드 OS의 배터리 최적화(Doze 모드) 및 메모리 킬러에 의해 앱이 종료되지 않도록 상주 보장
- **`SmsManager`:** UI를 띄우지 않고 다이렉트로 SMS(단문/장문)를 전송하는 시스템 하드웨어 인터페이스
- **`ContactsContract`:** 필요 시 2번 단말 내부 주소록에서 3번 번호를 동적으로 쿼리하는 프로바이더

---

## 2. 권한 및 매니페스트 구성 (`AndroidManifest.xml`)

백그라운드 통신과 상시 동작을 위해 Manifest 파일에 명시해야 하는 핵심 권한 및 컴포넌트 정의입니다.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.smsrelay">

    <!-- SMS 송수신 및 접근 권한 -->
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />

    <!-- 백그라운드 상주 및 부팅 자동 실행 권한 -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="SMS Relay Bridge"
        android:theme="@style/Theme.AppCompat.Light">

        <!-- SMS 수신 브로드캐스트 리시버 -->
        <receiver
            android:name=".SmsReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter android:priority="999">
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>

        <!-- 포그라운드 상주 서비스 -->
        <service
            android:name=".RelayService"
            android:foregroundServiceType="specialUse"
            android:exported="false" />
    </application>
</manifest>
```

---

## 3. 소스코드 구현 (Build)

수신된 문자를 필터링하여 전달하는 리시버와 서비스가 상시 유지되도록 돕는 포그라운드 서비스 구현체입니다.

### 3.1 SMS 수신 및 발송 처리기 (`SmsReceiver.kt`)

```kotlin
package com.example.smsrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TARGET_SENDER = "01012345678"     // 1번 폰 번호
        private const val TARGET_RECIPIENT = "01098765432"  // 3번 폰 번호
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: ""
                val body = sms.displayMessageBody ?: ""

                // 번호 정규화 후 일치 여부 판별
                val cleanSender = sender.replace("[^0-9]".toRegex(), "")
                val cleanTarget = TARGET_SENDER.replace("[^0-9]".toRegex(), "")

                if (cleanSender.endsWith(cleanTarget) || cleanTarget.endsWith(cleanSender)) {
                    Log.d("SmsRelay", "1번 폰으로부터 메시지 감지: $body")
                    relayMessage(context, TARGET_RECIPIENT, body)
                }
            }
        }
    }

    private fun relayMessage(context: Context, destination: String, text: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
            Log.d("SmsRelay", "3번 폰($destination)으로 릴레이 성공")
        } catch (e: Exception) {
            Log.e("SmsRelay", "SMS 릴레이 발송 실패", e)
        }
    }
}
```

### 3.2 상시 대기 포그라운드 서비스 (`RelayService.kt`)

```kotlin
package com.example.smsrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RelayService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, "relay_channel")
            .setContentTitle("SMS Relay 서비스 가동 중")
            .setContentText("백그라운드에서 수신 및 자동 전달 대기 중입니다.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // OS에 의해 비정상 종료 시 자동 재시작
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "relay_channel",
            "Relay Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
```

---

## 4. APK 빌드 및 릴리즈 (Packaging)

- **Step 1 (Keystore 생성):** 터미널에서 릴리즈용 서명 키 생성
  ```bash
  keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias relayKey
  ```
- **Step 2 (`build.gradle` 서명 설정):** `app` 레벨 `build.gradle`에 `signingConfigs.release` 블록 및 `buildTypes.release` 연결 설정
- **Step 3 (Assemble APK):** `./gradlew assembleRelease` 명령어 실행 → `app/build/outputs/apk/release/app-release.apk` 파일 생성

---

## 5. 2번 단말 배포 및 필수 세팅 (Deployment)

안드로이드 최신 버전(12~14+)의 백그라운드 제한을 우회하고 실시간으로 작동시키기 위해 2번 단말에서 반드시 수행해야 할 세부 설정입니다.

1. **사이드로딩 설치 허용:** 생성된 APK를 2번 단말기로 전송 후 '출처를 알 수 없는 앱 설치' 권한을 승인하여 수동 설치
2. **위험 권한(Dangerous Permissions) 승인:** `설정 > 애플리케이션 > SMS Relay Bridge > 권한` 진입 후 **SMS, 연락처**를 **'항상 허용'**으로 변경
3. **배터리 최적화 예외 (Doze Mode 우회):** `설정 > 애플리케이션 > SMS Relay Bridge > 배터리` 진입 후 **'제한 없음(Unrestricted)'**으로 지정
4. **화면 잠금 상태 테스트:** 2번 폰의 화면을 끈 상태에서 1번 폰으로 문자를 발송하고, 3번 폰으로 지연 없이 재전송되는지 최종 확인
