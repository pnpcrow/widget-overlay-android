# Android 위젯 플로팅 표시 기능 사전 타당성 보고서

> **결론:** 요청한 기능은 Android 네이티브로 구현할 수 있습니다. 다만 제3자 홈 화면 위젯을 호스팅하는 플로팅 오버레이는 고권한 UI이면서 사용자별 바인딩 승인이 필요한 고급 기능입니다. 따라서 **온디맨드 오버레이를 기본 경로**로 구현하고, **버블은 대화 또는 사용자가 명시적으로 요청한 소형 작업에 한정**하며, **Live Updates/Now Bar는 사용자가 시작한 시간 제한 진행 상황에만 보조 경로**로 적용해야 합니다.

## 1. 조사 범위와 판단 기준

본 조사는 Android 앱이 사용자가 선택한 App Widget을 호스트하여 화면 위에 잠시 표시하고, 미표시 상태에는 버블 또는 시스템 제공 실시간 표면을 선택적으로 이용할 수 있는지를 평가한다. 평가는 공개 Android 공식 문서와 Samsung 환경의 공개 기술 자료를 2026-08-15에 확인하여 수행했다.

| 대상 | 타당성 | 적정 역할 | 핵심 전제 |
|---|---|---|---|
| `AppWidgetHost` + 플로팅 오버레이 | **가능** | 제3자 위젯의 실제 `RemoteViews`를 온디맨드로 표시하는 주 경로 | 사용자가 위젯 추가를 승인하고, 오버레이 특별 접근 권한을 부여해야 함 |
| 앱 내/전면 Activity 호스트 | **가능** | 위젯 선택·설정·접근성 안내와 권한 회복 | 시스템 오버레이 권한 없이도 사용 가능 |
| Notification Bubble | **조건부 가능** | 대화 또는 사용자가 명시적으로 시작한 소형 앱 작업의 진입점 | 알림 기반, 사용자 설정에 종속, Android 11+에서는 대화 요건 필요 |
| Android Live Updates | **조건부 가능** | 사용자가 시작했고 끝이 명확한 진행·상태 표시 | Android 16 표준 API, ongoing 알림, 승격 가능성 및 사용자/OEM 정책에 종속 |
| Samsung Now Bar | **직접 제어 불가, 간접 가능** | Samsung 기기에서 표준 Live Updates의 OEM 표면으로 노출될 수 있는 보조 경로 | 별도 공개 안정 API가 아닌 OEM 통합 결과로 간주해야 함 |

## 2. 핵심 기술 타당성

### 2.1 제3자 위젯 호스팅

Android의 `AppWidgetHost`는 홈 화면과 같은 호스트 앱이 위젯을 자체 UI에 임베드하기 위한 프레임워크다. 호스트는 고정된 호스트 ID를 사용하고, 인스턴스별 `appWidgetId`를 할당한 뒤 `AppWidgetHostView`를 생성하여 위젯의 `RemoteViews`를 렌더링한다. 또한 호스트가 사라질 때 수신을 중지하고, 사용자가 위젯을 제거하면 ID를 해제해야 한다.[1] [2]

위젯 선택 및 바인딩은 앱이 임의로 수행해서는 안 된다. 일반 앱은 `ACTION_APPWIDGET_PICK` 시스템 선택기를 사용해 사용자가 제공자를 선택하도록 하고, 직접 바인딩이 허용되지 않는 경우에는 시스템의 `ACTION_APPWIDGET_BIND` 승인 흐름을 거쳐야 한다. 승인 후 제공자 설정 Activity가 있으면 이를 실행하고, 사용자가 취소하면 새 `appWidgetId`를 삭제해야 한다.[1] [3] 이는 본 기능이 단순한 화면 캡처가 아니라 **사용자와 플랫폼이 승인한 실제 위젯 인스턴스를 호스팅하는 방식**임을 의미한다.

> `AppWidgetHost`는 Activity가 보일 때 `startListening()`을 호출하고, 더 이상 보이지 않을 때 `stopListening()`을 호출하도록 API 문서가 안내한다.[2]

따라서 사용자의 "플로팅 뷰가 보일 때만 위젯 업데이트" 방침은 적절하다. 프로토타입에서는 오버레이가 열릴 때 리스너를 시작하고 실제 `AppWidgetHostView`를 부착하며, 닫을 때 제거·리스닝 중지한다. 바인딩된 위젯 ID 자체는 유지하여 다음 열기에서 재사용한다.

> **검증 중 정정:** Android Lint는 `BIND_APPWIDGET`를 일반 앱에 부여되지 않는 보호된 시스템 권한으로 판정했다. 따라서 이 프로토타입은 해당 권한을 매니페스트에 선언하지 않으며, 시스템 위젯 선택기와 사용자 승인 결과만 사용한다. 이는 개발 중 발견한 보안 제약을 반영한 최종 설계다.

### 2.2 화면 위 플로팅 뷰

다른 앱 위에 표시되는 일반 앱 오버레이는 Android 8.0 이상에서 `TYPE_APPLICATION_OVERLAY`로 구성하며, `SYSTEM_ALERT_WINDOW` 특별 접근 권한이 필요하다.[4] 이 윈도우는 일반 Activity 윈도우보다 위, 상태 표시줄·IME 같은 핵심 시스템 윈도우보다 아래에 표시된다.[5] 사용자가 권한을 거부하거나 나중에 철회할 수 있으므로, 앱은 권한을 요구하는 화면과 권한 없는 대체 경로를 모두 제공해야 한다.

Android 11 이상에서는 오버레이 관리 인텐트가 특정 앱의 상세 화면이 아닌 최상위 특별 접근 설정 화면으로 이동할 수 있으므로, 앱은 설정 복귀 후 `Settings.canDrawOverlays()`로 결과를 재확인해야 한다.[6] Google Play의 권한 정책도 민감·특별 권한을 현재 사용자 기능에 필요한 범위로 요청하고 시스템 설정 화면에서 승인을 받도록 요구한다.[7] 그러므로 이 프로젝트는 앱 시작 직후 권한을 강요하지 않고, 사용자가 "화면 위에 표시"를 켰을 때만 목적을 설명한 뒤 설정으로 안내한다.

### 2.3 버블은 범용 대체 UI가 아니다

알림 버블의 확장 UI는 개발자가 지정한 Activity이며, 이 Activity는 `resizeableActivity`와 `allowEmbedded`가 필요하다. 버블은 사용자 설정에 따라 전체 또는 개별적으로 차단될 수 있고, 잠금 상태에서는 일반 알림처럼 보인다.[8] Android 11 이상에서는 대화 요건을 충족해야 버블이 되며, 유효한 장기 공유 바로가기와 `MessagingStyle` 등 대화 중심의 구성 요소가 필요하다.[8] [9]

따라서 이 앱은 버블에 외부 위젯을 항상 표시하려는 방식이 아니라, 선택된 위젯의 상태를 열어보는 **앱 자체의 작은 요약 Activity**를 버블로 제공한다. 해당 버블은 사용자의 명시적 요청이 있고 콘텐츠가 대화·작업 단위로 자연스러운 경우에만 생성한다. 그렇지 않으면 일반 알림 또는 오버레이의 버블 모양 미니 런처를 사용한다.

### 2.4 Live Updates 및 Samsung Now Bar

Android Live Updates는 앱을 열지 않고도 중요 진행 상황을 추적하도록 승격되는 ongoing 알림이다. 표준/BigText/Call/Progress/Metric 스타일 중 하나여야 하고, `POST_PROMOTED_NOTIFICATIONS`를 선언하며, `setRequestPromotedOngoing(true)` 또는 동등한 플랫폼 extra로 승격을 요청해야 한다. 커스텀 `RemoteViews`는 허용되지 않고 사용자가 승격을 해제할 수 있다.[10]

Android 공식 UX 기준은 Live Updates를 **진행 중**, **사용자 시작**, **시간 민감** 활동에만 사용하라고 명시한다. 일반 기능에 대한 빠른 진입점에는 앱 위젯이나 Quick Settings Tile을 사용하라고 권고하며, 광고·일반 알림·다가오는 일정·단순 기능 접근에는 사용하면 안 된다.[10] 따라서 Live Update는 위젯 자체의 대체 표면이 아니라 예를 들어 사용자가 타이머·배송·내비게이션·동기화를 시작한 경우의 진행 상태에만 적용한다.

Samsung Now Bar는 앱이 직접 호출하는 공개 Android 표준 API가 아니다. One UI 7의 Live Notifications/Now Bar는 일반 앱에 제한적이었고, 공개 분석은 Android 16 Live Updates를 통한 통합이 기기·One UI 버전 및 설정에 따라 달라질 수 있음을 보고한다.[11] [12] 따라서 구현은 **표준 Android Live Updates만 생성**하고, Samsung 기기에서 Now Bar에 표시되는지 여부는 기능 탐지와 실기기 검증으로 확인한다. Samsung 전용 비공개 extra, 화이트리스트 또는 개발자 옵션을 제품 기능의 의존성으로 삼지 않는다.

## 3. 업데이트·배터리 전략

위젯 갱신은 전체 갱신보다 부분 갱신이 저렴하지만, 외부 제공자 위젯은 제공자가 전송하는 `RemoteViews`의 갱신 주기를 최종적으로 통제한다. 호스트 앱은 오버레이가 열렸을 때 `startListening()`하여 가장 최근 상태를 받고, 닫았을 때 `stopListening()`한다.[2] [13] 이 방식은 별도 반복 작업으로 위젯을 강제 갱신하기보다 사용자가 실제로 보는 순간에 갱신을 수신한다.

앱 고유 메타데이터나 저장된 표시 상태에 한해 매우 긴 주기의 보조 동기화가 필요하면 `WorkManager`를 이용한다. Android 위젯의 `updatePeriodMillis`는 30분보다 짧게 설정할 수 없고, `0`으로 비활성화한 후 `WorkManager`로 빈도를 제어할 수 있다. 단, 반복 작업에도 전력 제약이 적용되므로 정확한 시각 실행을 기대해서는 안 된다.[13] 프로토타입은 기본값을 **24시간**으로 두며 네트워크·충전 조건은 실제 데이터 원천이 필요할 때만 추가한다.

| 시점 | 수행 작업 | 백그라운드 네트워크 | 설계 의도 |
|---|---|---|---|
| 위젯 선택·설정 | `appWidgetId` 할당, 시스템 바인딩/설정 Activity, 식별자 영속화 | 제공자 설정에 따름 | 명시적 사용자 행위만 허용 |
| 오버레이 표시 | `startListening()`, `AppWidgetHostView` 생성, 크기 옵션 전달 | 별도 강제 호출 없음 | 보이는 순간 실제 제공자 업데이트 수신 |
| 오버레이 숨김 | View 제거, `stopListening()` | 없음 | 리소스·수신 범위 최소화 |
| 24시간 보조 작업 | 선택 항목의 유효성 검사 및 앱 내부 메타데이터 정리 | 기본 없음 | 기기 전력 정책을 존중하는 제한적 유지보수 |
| 사용자가 시작한 여정 | Live Update 알림 갱신 | 상황별 | Live Update UX 요건을 만족할 때만 사용 |

## 4. 제품 설계 권고안

기본 사용자 흐름은 "위젯 선택 → 제공자 권한/설정 → 오버레이 권한 요청 → 미니 버블 표시 → 탭하면 실제 위젯 오버레이 표시"로 구성한다. 오버레이가 허용되지 않으면 앱 Activity에서 동일한 `AppWidgetHostView`를 표시하고, 사용자에게 시스템 설정을 다시 열 수 있는 선택지만 제공한다. 오버레이에는 닫기, 드래그 이동, 최소화, 제거의 명시적 컨트롤을 제공해 다른 앱의 입력을 가로채지 않도록 한다.

버블과 Live Update는 **표면 선택 정책**의 부수 경로다. 버블이 플랫폼·사용자 설정에서 가능하고 콘텐츠가 대화/작업 의미를 만족할 때만 버블을 요청한다. Android 16 이상에서 사용자가 시작한 시간 제한 작업이라면 Live Update를 먼저 시도하고, 시스템이 승격하지 않거나 사용자가 껐으면 표준 ongoing 알림으로 우아하게 하향한다. Samsung Now Bar는 이 표준 Live Update의 결과 노출 표면으로만 취급한다.

## 5. 프로토타입 범위 및 알려진 한계

프로토타입은 Android 26 이상에서 동작하도록 작성하며, Android 16(API 36) 이상에서만 Live Update 승격 요청을 활성화한다. 외부 위젯 제공자마다 최소 크기, 리사이즈 지원, 구성 Activity, 컬렉션 뷰 동작이 다르므로 Google Calendar·Clock 등 최소 3개 제공자로 실기기 검증이 필요하다. 프로토타입은 오버레이 호스트·버블·Live Update의 표준 API 흐름을 보여 주되, 임의의 외부 위젯이 모든 OEM에서 동일하게 렌더링된다는 보장은 하지 않는다.

또한 이 앱은 다른 앱의 위젯을 **그 위젯이 제공하는 `RemoteViews` 범위에서만** 표시한다. 제공자가 데이터 업데이트를 보내지 않으면 호스트가 비표시 상태에서 최신 데이터를 생성할 수 없으며, 제공자의 클릭 `PendingIntent`가 별도 Activity를 열거나 오버레이를 닫을 수 있다. 이는 위젯 호스팅 모델의 정상적인 제공자 소유 경계다.[1] [2]

## 참고문헌

[1] [Android Developers, Build a widget host](https://developer.android.com/develop/ui/views/appwidgets/host)

[2] [Android Developers, AppWidgetHost API reference](https://developer.android.com/reference/android/appwidget/AppWidgetHost)

[3] [Android Developers, AppWidgetManager—ACTION_APPWIDGET_BIND](https://developer.android.com/reference/android/appwidget/AppWidgetManager#ACTION_APPWIDGET_BIND)

[4] [Android Developers, Handle input method visibility—Create an overlay view](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility)

[5] [Android Developers, WindowManager.LayoutParams—TYPE_APPLICATION_OVERLAY](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)

[6] [Android Developers, Permissions updates in Android 11](https://developer.android.com/about/versions/11/privacy/permissions)

[7] [Google Play Console Help, Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241)

[8] [Android Developers, Use notification bubbles for conversations](https://developer.android.com/develop/ui/compose/notifications/bubbles)

[9] [Android Developers, People and conversations](https://developer.android.com/develop/ui/views/notifications/conversations)

[10] [Android Developers, Create live update notifications](https://developer.android.com/develop/ui/compose/notifications/live-update)

[11] [Akexorcist, Live Notifications and Now Bar in Samsung One UI 7: As developer](https://akexorcist.dev/live-notifications-and-now-bar-in-samsung-one-ui-7-as-developer-en/)

[12] [Android Authority, One UI 8 will let any app show a Live Notification in Samsung's Now Bar](https://www.androidauthority.com/one-ui-8-live-updates-support-3573794/)

[13] [Android Developers, Create an advanced widget](https://developer.android.com/develop/ui/views/appwidgets/advanced)

