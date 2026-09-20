# Hi_Dorm SMS Relay Bridge (Android)

> 기숙사 통합 관리 시스템(Hi_Dorm)과 연동하여 안드로이드 공기계(알뜰폰 무제한 문자 요금제 USIM)를 통해 학생들에게 SMS/LMS 안내 문자를 저비용·고신뢰성으로 중계 발송하는 안드로이드 브릿지 애플리케이션입니다.

---

## 📌 주요 특징

- **양방향 하이브리드 통신:** WebSocket (실시간 푸시) + HTTP Polling (네트워크 단절 시 백업 폴링 3~5초)
- **무중단 24/7 상주 시스템:** Foreground Service, Doze Mode(배터리 최적화) 해제, 부팅 시 자동 시작, 워치독 자동 복구
- **통신사 스팸 방지 락:** 
  - 건당 가변 딜레이(1.5초~3초) 제어로 통신사 기지국 스팸 필터 회피
  - 일일 발송 상한(기본 450건) 도달 시 자동 일시정지로 번호 이용정지 사전 차단
- **로컬 큐잉 (Room DB):** 네트워크가 끊겨도 발송 요청이 유실되지 않도록 SQLite에 영속화
- **양방향 소통 (Inbound Relay):** 사생이 보낸 답장/회신 문자를 감지하여 백엔드 Webhook으로 실시간 포워딩
- **실시간 관제 (Telemetry):** 단말기 배터리 잔량, 충전 상태, Wi-Fi/LTE 신호 상태를 서버로 주기적 보고 (Heartbeat 30초)
- **사내 전용 APK 배포 (Sideloading):** 구글 플레이 `SEND_SMS` 정책 제약을 회피하기 위한 GitHub Actions 기반 Release APK 자동 빌드 및 자체 서명 배포

---

## 📁 프로젝트 구조

```text
Hi_Dorm_SMS/
├── .github/workflows/
│   └── build-apk.yml               # GitHub Actions APK 자동 빌드 & 릴리즈 워크플로우
├── app/
│   ├── build.gradle.kts            # 앱 모듈 빌드 스크립트 (SDK 34, Room, Retrofit, Coroutines)
│   ├── proguard-rules.pro          # ProGuard 난독화/최적화 규칙
│   └── src/main/
│       ├── AndroidManifest.xml     # SMS, 포그라운드, 부팅, 워치독 권한 및 컴포넌트 선언
│       ├── java/com/hidorm/smsrelay/
│       │   ├── HiDormRelayApp.kt   # Application 클래스 (알림 채널, 싱글톤 컨테이너)
│       │   ├── data/
│       │   │   ├── local/          # Room DB (MessageEntity, MessageDao, AppDatabase)
│       │   │   ├── remote/         # Retrofit ApiService, WebSocketManager, DTO
│       │   │   └── repository/     # RelayRepository (로컬 큐, 네트워크, 설정 동기화)
│       │   ├── modem/
│       │   │   └── SmsSender.kt    # SmsManager 연동, Multipart 분할 및 ResultCode 추적
│       │   ├── service/
│       │   │   ├── SmsRelayForegroundService.kt  # 24/7 포그라운드 상주 발송 엔진
│       │   │   ├── BootReceiver.kt               # 기기 부팅 시 자동 시작 리시버
│       │   │   ├── SmsReceiver.kt                # 사생 답장 SMS 감지 웹훅 리시버
│       │   │   └── WatchdogReceiver.kt           # 프로세스 생존 감시 워치독
│       │   └── ui/
│       │       ├── MainActivity.kt               # 대시보드 (통계, 실시간 로그, 테스트 발송)
│       │       ├── MainViewModel.kt              # 대시보드 비즈니스 로직 및 LiveData
│       │       └── SettingsActivity.kt           # 서버 URL, 토큰, 딜레이, 한도 설정 화면
│       └── res/                                  # 레이아웃 및 리소스 파일
├── SPECIFICATION.md                # 종합 개발 및 배포 기술 명세서
└── README.md
```

---

## 🚀 빌드 및 실행 방법

### 1. Android Studio에서 열기
1. Android Studio를 실행하고 **Open**을 클릭하여 `Hi_Dorm_SMS` 폴더를 엽니다.
2. Gradle 동기화(Sync)가 완료되면 상단 실행 대상에서 연결된 실제 Android 단말기를 선택합니다.
3. **Run 'app'** 버튼(녹색 삼각형)을 클릭하여 단말기에 설치 및 실행합니다.

### 2. 명령줄(CLI)에서 APK 빌드
```bash
# Debug APK 빌드
./gradlew assembleDebug

# Release APK 빌드
./gradlew assembleRelease
```
빌드된 APK 파일 위치:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

### 3. ADB를 통한 단말기 직접 설치
```bash
adb install -r -d app/build/outputs/apk/release/app-release.apk
```

---

## 📱 공기계 단말기 현장 설정 체크리스트

단말기를 기숙사 서버실 또는 관리실에 상시 거치할 때 아래 설정을 완료해야 합니다:

1. **배터리 최적화 해제:**
   - `설정 > 애플리케이션 > Hi_Dorm SMS Relay > 배터리 > 제한 없음(Unrestricted)`
2. **배터리 보호(수명 연장) 모드:**
   - 24시간 상시 충전 케이블 연결 시 배터리 스웰링(부풀림) 방지를 위해 `설정 > 배터리 > 배터리 보호 (최대 80~85% 충전 제한)` 활성화
3. **화면 켜짐 유지 (개발자 옵션):**
   - `설정 > 개발자 옵션 > 충전 중 화면 켜짐 유지` 설정 권장
4. **권한 일괄 승인:**
   - 앱 최초 기동 시 표시되는 SMS 전송/수신, 전화 상태, 알림 권한을 모두 "허용"합니다.

---

## 📄 기술 명세서

자세한 시스템 아키텍처, 통신 프로토콜, 통신사 스팸 방지 규정 및 API 명세는 프로젝트 루트의 [SPECIFICATION.md](file:///C:/Users/User/orca/projects/Hi_Dorm_SMS/SPECIFICATION.md)를 참조하세요.
