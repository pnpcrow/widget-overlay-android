# 조사 노트

## 2026-08-15: Android AppWidgetHost

Android 공식 문서에 따르면 제3자 App Widget을 앱 내부에 임베드하는 커스텀 호스트는 `AppWidgetHost`를 구현할 수 있습니다. 호스트 ID는 패키지 안에서 고유해야 하며 유지됩니다. 각 위젯 인스턴스에는 `allocateAppWidgetId()`로 할당한 ID가 필요하고, 제거 시 `deleteAppWidgetId()`를 호출해야 합니다. 호스트는 `AppWidgetHostView`로 `RemoteViews`를 표시하고, 크기 범위를 `updateAppWidgetSize()` 등 옵션 번들로 제공해야 합니다.

위젯 바인딩에는 `android.permission.BIND_APPWIDGET` 선언이 필요하며, 런타임에는 사용자가 위젯을 현재 호스트에 추가하도록 명시적으로 허용해야 합니다. `bindAppWidgetIdIfAllowed()`가 거짓이면 시스템 제공 권한 승인 흐름을 열어야 합니다. 위젯에 설정 Activity가 있으면 일반적으로 이를 실행해야 합니다. 이 기능은 "홈 화면 대체 또는 유사한 앱"을 위한 계약 책임이 큰 고급 기능으로 규정되어 있습니다.

출처: Android Developers, "Build a widget host" (2026-08-15 열람)
https://developer.android.com/develop/ui/views/appwidgets/host

## 주의 사항

본 조사 노트는 중간 기록입니다. 최종 판단과 설계는 `research/feasibility_report.md`에서 출처와 함께 정리합니다.

## 2026-08-15: 알림 버블 조사 진행

Android Developers의 "Use notification bubbles for conversations" 문서를 확인 대상으로 선정했습니다. 문서의 제목과 탐색 구조는 버블을 알림 시스템의 기능, 특히 대화 중심 콘텐츠를 위한 기능으로 분류하고 있습니다. 따라서 앱 위젯을 직접 렌더링하는 일반 오버레이의 동일한 대체물로 간주하지 않고, 사용자 주도적이고 지속성이 있는 소규모 작업 또는 대화형 진행 상황에만 선택적으로 사용해야 한다는 가설을 세웠습니다.

브라우저 세션의 후속 본문 추출은 빈 페이지로 전환되어 본문 인용을 확보하지 못했습니다. 다음 단계에서 문서 직접 추출 또는 다른 공식 문서로 원문을 보강합니다.

대상 출처:
https://developer.android.com/develop/ui/compose/notifications/bubbles

