# Hi_Dorm SMS Relay Bridge (Android)

> 기숙사 통합 관리 시스템(Hi_Dorm)과 연동하여 안드로이드 공기계(알뜰폰 무제한 문자 요금제 USIM)를 통해 학생들에게 SMS/LMS 안내 문자를 저비용·고신뢰성으로 중계 발송하는 고성능 안드로이드 브릿지 애플리케이션입니다.  
> **Apple StandBy / iOS 17-18 Dark Mode 디자인 시스템** 및 **직결 P2P 자동 포워딩(1번➔2번➔3번)** 아키텍처를 완벽 지원합니다.

---

## 📌 주요 특징

- **Apple StandBy & iOS 17-18 다크 디자인 시스템:**
  - OLED 트루 블랙(`#000000`) 0 nit 소등 배경
  - G2 연속 곡률 22dp 스퀴클 카드(`apple_card_bg`), 14dp 동심 컴포넌트(`apple_card_inner`)
  - Apple Watch 메트릭 링 스타일의 일일 발송 쿼터 게이지 및 다이내믹 알약 뱃지(Live Pill Badges)
- **독립형 직결 P2P 자동 포워딩 (1번➔2번➔3번):**
  - 서버 없이 1번 단말이 발송한 트리거 SMS를 2번 브릿지 단말이 백그라운드 수신 후 즉시 3번 단말로 자동 릴레이
  - E.164 표준 번호 정규화 (`+82 10` ➔ `010` 국가코드 자동 보정)
  - EUC-KR 90바이트 자동 계산을 통한 SMS/LMS 최적화 분기 및 발신자 접두사 포맷팅
- **양방향 하이브리드 통신 (클라우드 연동):**
  - WebSocket (실시간 푸시 발송) + HTTP Polling (네트워크 단절 시 3~5초 백업 폴링 자동 전환)
- **무중단 24/7 상주 시스템:**
  - Foreground Service + WakeLock(20초) 보호로 Doze 모드(화면 꺼짐/딥 슬립) 완벽 극복
  - Direct Boot (FBE 잠금 해제 전 부팅 상주) 및 워치독 프로세스 생존 주기 보장
- **통신사 스팸 방지 락 & 하드웨어 텔레메트리:** 
  - 건당 가변 딜레이(1.5초~3초) 제어로 통신사 기지국 스팸 필터 회피
  - 일일 발송 상한(기본 450건) 도달 시 자동 일시정지로 번호 이용정지 사전 차단
  - 45°C 이상 배터리 고온 감지 시 스마트 쿨다운(30초 대기)
- **7대 엣지케이스 자동 시뮬레이션 엔진:**
  - 앱 내 원클릭(`🧪 7대 엣지케이스 자동 시뮬레이션`)으로 국가번호, 장문 LMS 분할, 번호 정규화, 스팸 방지 등 사전 검증
- **로컬 큐잉 (Room DB):**
  - 전송 및 릴레이 내역 영속화 (`SENT`, `FAILED`, `DISPATCHED` 상태 추적)

---

## 📁 프로젝트 구조

```text
Hi_Dorm_SMS/
├── .github/workflows/
│   └── build-apk.yml               # GitHub Actions APK 자동 빌드 & 릴리즈 워크플로우
├── docs/
│   ├── DEVICE_SETUP_GUIDE.md       # 안드로이드 12~14+ 백그라운드 무제한 상주 7단계 설정
│   ├── SMS_AUTO_FORWARDING_GUIDE.md# 1번➔2번➔3번 자동 포워딩 시스템 설계 및 가이드
│   ├── SIMULATION_AND_EDGE_CASE_REPORT.md # 7대 통신사 엣지케이스 분석 및 시뮬레이션 리포트
│   └── SMS_Relay_Implementation_Guide.md # Word 기획서 원문 이관 가이드
├── app/
│   ├── build.gradle.kts            # 앱 모듈 빌드 스크립트 (SDK 34, Room, Retrofit, Coroutines)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # 권한, 리시버, 포그라운드 서비스 정의
│       │   ├── java/com/hidorm/smsrelay/
│       │   │   ├── HiDormRelayApp.kt   # Application 클래스 (알림 채널, 싱글톤 컨테이너)
│       │   │   ├── data/
│       │   │   │   ├── local/          # Room DB (MessageEntity, MessageDao, AppDatabase)
│       │   │   │   ├── remote/         # Retrofit ApiService, WebSocketManager, DTO
│       │   │   │   └── repository/     # RelayRepository (로컬 큐, 네트워크, 설정 동기화)
│       │   │   ├── modem/
│       │   │   │   └── SmsSender.kt    # SmsManager 연동, Multipart 분할 및 ResultCode 추적
│       │   │   ├── service/
│       │   │   │   ├── SmsRelayForegroundService.kt  # 24/7 포그라운드 상주 발송 엔진
│       │   │   │   ├── BootReceiver.kt               # 기기 부팅 시 자동 시작 리시버
│       │   │   │   ├── SmsReceiver.kt                # SMS 수신 감지 및 1➔3 직결 포워딩 리시버
│       │   │   │   └── WatchdogReceiver.kt           # 프로세스 생존 감시 워치독
│       │   │   ├── simulator/
│       │   │   │   └── SmsSimulationEngine.kt        # 7대 엣지케이스 자동 시뮬레이터
│       │   │   ├── util/
│       │   │   │   ├── PhoneNumberNormalizer.kt      # 한국 전화번호 정규화 및 E.164 파싱
│       │   │   │   └── SmsMessageFormatter.kt        # EUC-KR 90바이트 계산 및 LMS 포맷터
│       │   │   └── ui/
│       │   │       ├── MainActivity.kt               # StandBy 메인 대시보드
│       │   │       ├── MainViewModel.kt              # 대시보드 뷰모델
│       │   │       ├── SettingsActivity.kt           # 서버 및 포워딩 설정 화면
│       │   │       ├── HistoryActivity.kt            # 전송 내역 화면
│       │   │       └── HistoryAdapter.kt             # Apple Pill 뱃지 내역 어댑터
│       │   └── res/
│       │       ├── drawable/                         # Apple StandBy 스퀴클/알약 드로어블
│       │       └── layout/                           # OLED Black 3단 UI 레이아웃
│       └── test/                                     # 단위 테스트 (정규화, 바이트 계산, 필터)
├── SPECIFICATION.md                # 종합 개발 및 배포 기술 명세서
└── README.md
```

---

## 🚀 빌드 및 설치 방법

### 1. GitHub Actions CI 빌드 (권장)
코드 푸시 시 `.github/workflows/build-apk.yml` 워크플로우가 자동으로 실행되어 Release/Debug APK를 생성합니다.
- 생성된 APK는 GitHub Actions Artifacts에서 다운로드할 수 있습니다:
  - `hidorm-sms-relay-release-apk`
  - `hidorm-sms-relay-debug-apk`

### 2. 단말기 직접 설치 (ADB)
```bash
adb install -r -d app-release.apk
```

---

## 📱 2번 중계 단말기 핵심 셋업 (안드로이드 12~14+)

자세한 설정 방법은 [docs/DEVICE_SETUP_GUIDE.md](file:///C:/Users/User/orca/projects/Hi_Dorm_SMS/docs/DEVICE_SETUP_GUIDE.md)를 확인하세요:

1. **배터리 최적화 완전 무제한:**
   `설정 > 애플리케이션 > Hi_Dorm SMS > 배터리 > 제한 없음(Unrestricted)`
2. **백그라운드 사용 제한 해제:**
   `절전 예외 앱` 및 `데이터 절약 모드 제외` 등록
3. **충전 중 화면 켜짐 유지 (개발자 옵션):**
   `설정 > 개발자 옵션 > 충전 중 화면 켜짐 유지(Stay Awake)`
4. **팬텀 프로세스 킬러(Phantom Process Killer) 무력화:**
   ```bash
   adb shell device_config put activity_manager max_phantom_processes 2147483647
   ```
5. **Direct Boot 지원:**
   기기가 재부팅된 직후 잠금 화면을 풀지 않아도 백그라운드 리시버가 즉시 활성화됩니다.
