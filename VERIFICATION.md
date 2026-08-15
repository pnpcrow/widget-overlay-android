# 검증 기록

## 자동 검증 결과

2026-08-15에 Android SDK Platform 36과 JDK 21 환경에서 아래 명령을 실행했다.

```bash
./gradlew --no-daemon --max-workers=1 \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  :app:lintDebug
```

> **결과: BUILD SUCCESSFUL** — Debug APK 생성, `SurfacePolicyTest` 단위 테스트 실행, Android Lint 검사 완료.

| 검증 항목 | 결과 | 근거 산출물 |
|---|---|---|
| Kotlin/Java 컴파일 | 통과 | `app/app/build/outputs/apk/debug/app-debug.apk` |
| Debug APK 패키징 | 통과 | 동일 APK |
| 단위 테스트 | 통과 | `app/app/build/reports/tests/testDebugUnitTest/` |
| Android Lint | 통과 | `app/app/build/reports/lint-results-debug.html` |
| 매니페스트 보호 권한 | 수정·재검증 통과 | `BIND_APPWIDGET` 선언 제거 후 Lint 성공 |

## 보안 제약 확인 및 반영

초기 정적 검증에서 `BIND_APPWIDGET`가 일반 앱에 부여되지 않는 보호된 시스템 권한으로 보고되었다. 이에 따라 매니페스트 선언을 제거했고, 앱은 시스템 위젯 선택기와 사용자의 명시적 승인 결과만을 사용하도록 확정했다. 이는 커스텀 호스트가 외부 위젯을 임의로 바인딩하지 않도록 하는 중요한 경계다.

## 남은 실기기 검증

자동 빌드는 UI와 OEM 정책을 완전히 재현하지 못한다. 릴리스 전에는 `plan/implementation_and_verification_plan.md`의 MAN-01부터 MAN-10까지를 실제 기기에서 실행해야 한다. 특히 `SYSTEM_ALERT_WINDOW` 승인·철회, 여러 제공자의 구성 Activity, 버블 사용자 설정, Android 16 Live Update 승격, Samsung Now Bar 노출은 에뮬레이터 빌드 성공만으로 보장되지 않는다.

