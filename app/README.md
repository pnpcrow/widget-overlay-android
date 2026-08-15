# Widget Overlay Prototype

이 폴더는 **Android 네이티브 Kotlin**으로 작성한 위젯 오버레이 프로토타입이다. 사용자가 시스템 위젯 선택기에서 App Widget을 고른 뒤, 실제 `AppWidgetHostView`를 필요할 때만 앱 내부 또는 다른 앱 위 오버레이에 표시한다. 위젯 제공자의 데이터를 앱이 복사하거나 별도 주기로 가져오지 않는다.

## 기능 경계

| 기능 | 구현 상태 | 동작 원칙 |
|---|---|---|
| 제3자 위젯 선택·설정 | 구현 | 시스템 선택기와 제공자 구성 Activity를 사용한다. |
| 온디맨드 플로팅 오버레이 | 구현 | 사용자가 특별 접근 권한을 부여하고 직접 열 때만 실행한다. |
| 미니 런처 | 구현 | 드래그 가능하며 탭하면 실제 위젯 패널을 연다. |
| 앱 내부 미리보기 | 구현 | 오버레이 권한이 없을 때의 기능 대체 경로다. |
| 버블 | 구현 | 앱 소유의 요약 Activity를 버블로 요청하고, 미지원 시 일반 알림으로 하향한다. |
| Live Update | 구현 | API 36 이상에서 사용자가 시작한 테스트 진행 상태에 한해 승격을 요청한다. |
| Samsung Now Bar | 간접 검증 | 표준 Live Update가 OEM 표면에 노출되는지 실기기에서만 확인한다. |
| 유지보수 | 구현 | 24시간 `WorkManager` 작업은 저장 위젯 ID의 유효성만 점검한다. |

## 요구 환경

프로젝트는 `compileSdk 36`, `targetSdk 36`, `minSdk 26`을 사용한다. Android Studio 최신 안정판과 Android SDK Platform 36을 설치한 환경에서 여는 것을 권장한다. Android 16 이상의 Live Update 승격은 시스템 설정과 OEM 정책에 의해 일반 ongoing 알림으로 남을 수 있다.

## 빌드와 실행

```bash
./gradlew :app:assembleDebug
```

생성 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 위치한다. 설치 후 다음 순서로 검증한다.

1. **위젯 선택**을 눌러 시스템 선택기에서 하나를 고르고, 제공자 설정이 있다면 완료한다.
2. 앱 내부 미리보기로 실제 `RemoteViews`가 렌더링되는지 확인한다.
3. **오버레이 열기**를 누르고 안내에 따라 "다른 앱 위에 표시" 특별 접근을 허용한다.
4. 홈 화면 또는 다른 앱에서 미니 런처를 탭해 위젯 패널이 열리고, 최소화·닫기가 정상 동작하는지 확인한다.
5. 알림 권한을 허용한 상태에서 버블 및 진행 상태 테스트를 실행한다.
6. API 36 이상의 Samsung 기기에서는 Live Update가 Now Bar에 노출되는지 기기·One UI 버전·개발자 옵션과 함께 기록한다.

## 권한과 개인정보

`SYSTEM_ALERT_WINDOW`는 사용자가 오버레이 기능을 요청한 경우에만 시스템 설정 화면에서 승인하도록 한다. `POST_NOTIFICATIONS`는 버블·진행 상태 알림을 위해 Android 13 이상에서 런타임으로 요청한다. 이 앱은 인터넷 권한, 위치·연락처·미디어 권한을 요청하지 않으며, 선택 위젯의 콘텐츠를 자체 저장소에 보관하지 않는다.

## 알려진 한계

각 위젯 제공자가 지원하는 크기, 구성 Activity, 컬렉션 UI, 클릭 동작은 서로 다르므로 모든 타사 위젯이 동일한 모양으로 렌더링된다고 보장할 수 없다. 오버레이 특별 접근은 사용자 또는 기기 관리자가 거부·철회할 수 있으며, 이 경우 앱 내부 표시가 유일한 대체 경로다. 버블과 Now Bar 노출은 앱이 보장할 수 없는 시스템·사용자·OEM 결정이다.

세부 조사와 테스트 행렬은 상위 폴더의 [`research/feasibility_report.md`](../research/feasibility_report.md) 및 [`plan/implementation_and_verification_plan.md`](../plan/implementation_and_verification_plan.md)을 참조한다.

