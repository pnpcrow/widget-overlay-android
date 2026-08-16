# Widget Overlay

서드파티 앱 위젯을 `AppWidgetHost`로 호스팅하여, 다른 앱 위에서도 언제든 띄울 수 있는 Android 오버레이 앱입니다. 홈 런처에 추가하듯 위젯을 고르면 실제 `AppWidgetHostView`(RemoteViews)를 그대로 렌더링하며, 위젯 데이터를 앱이 복사하거나 별도 주기로 폴링하지 않습니다. Material You 동적 색상과 다크 모드를 지원하는 Material 3 디자인 시스템 위에 구축되어 있습니다.

- **언어/UI**: Kotlin, View 기반 (XML 레이아웃 최소화 — 프로그래매틱 UI + 공용 컴포넌트)
- **최소 지원**: Android 8.0 (API 26) / **타깃·컴파일**: API 36

## 주요 기능

| 기능 | 설명 |
|---|---|
| 위젯 선택기 | 설치된 모든 위젯 제공자를 앱별로 그룹핑해 검색 + 2열 그리드로 탐색. 시스템 바인드 승인 다이얼로그와 제공자의 설정(구성) Activity 플로우를 지원합니다. |
| 위젯 스택 | 여러 위젯을 추가하고 오버레이/미리보기에서 좌우 스와이프로 전환. 웜(worm) 페이지 인디케이터가 현재 위치를 표시합니다. |
| 오버레이 패널 | 반투명 스크림이 뒤 화면을 가리고 터치를 차단하는 플로팅 패널. 그랩 핸들 드래그로 위치를 자유롭게 옮기고, 탭하면 최소화됩니다. 새로고침은 창을 재생성하지 않고 그 자리에서 로딩 → 콘텐츠 교체됩니다. |
| 미니 런처 버블 | 패널 최소화 시 남는 56dp 원형 버블. 드래그로 옮길 수 있고 탭하면 패널이 다시 열립니다. |
| 앱 내 미리보기 | 오버레이 권한이 없는 환경의 대체 경로. 메인 화면에서 실제 위젯 뷰를 렌더링합니다. |
| Material You | Android 12+에서 배경화면 기반 동적 색상, 시스템 다크 모드 자동 전환, 그 이하 버전에서는 정적 M3 팔레트 폴백. |

## 화면 안내

### 메인 화면 (`MainActivity`)
- **선택된 위젯 카드**: 현재 미리보기 페이지의 위젯 이름, 실제 위젯 렌더링 미리보기(ViewPager2 + 웜 인디케이터), 스택 개수, 위젯 추가/해제 버튼.
- **표시 방식 카드**: 오버레이 열기 / 미니 런처 표시 / 앱 안에서 열기 / 오버레이 숨기기.
- 페이저를 넘기면 위젯 이름 라벨이 해당 페이지 위젯을 따라갑니다.

### 위젯 선택기 (`WidgetPickerActivity`)
- M3 서치바 스타일 검색창(위젯 이름·앱 이름 필터)과 앱 그룹 헤더 + 2열 미리보기 카드 그리드.
- 항목 선택 → 시스템 바인드 승인 → (설정이 필요한 위젯이면) 제공자 설정 화면 이동 → 완료 후 돌아오면 스택에 추가됩니다. 설정 activity는 결과 전달을 위해 반드시 같은 태스크에서 실행됩니다.

### 오버레이 (`OverlayService`)
전경 서비스(specialUse FGS)가 세 종류의 오버레이 창을 관리합니다:

| 창 | 역할 |
|---|---|
| 스크림 | 전체 화면 반투명(50%) 레이어. 시스템바 영역까지 덮으며 뒤 앱으로 터치가 새지 않고, 탭하면 패널을 최소화합니다. |
| 패널 | 28dp 코너 반투명 톤 surface + 헤어라인 보더. 그랩 핸들(드래그=이동/탭=최소화), 새로고침·최소화 아이콘 버튼, 위젯 페이저 + 웜 인디케이터로 구성. |
| 버블 | 최소화 상태의 56dp 원형 런처. `ic_apps` 아이콘, 드래그 가능, 탭하면 패널 확장. |

패널 위치는 드래그 종료 시 dp 단위로 저장되어 다음에 열 때 복원됩니다(화면 밖으로는 클램프). 버블 위치는 서비스 세션 동안 유지됩니다.

## 아키텍처

```
widget-overlay/                 ← Git 저장소 루트
└── app/                        ← Gradle 프로젝트 루트
    ├── app/src/main/java/com/example/widgetoverlay/
    │   ├── MainActivity.kt            메인 화면 (선택/미리보기/표시 방식)
    │   ├── WidgetPickerActivity.kt    위젯 선택기 (검색 + 그리드, 바인드/설정 플로우)
    │   ├── OverlayService.kt          오버레이 전경 서비스 (스크림/패널/버블 창 관리)
    │   ├── WidgetHostController.kt    AppWidgetHost 계약 단일 소유 (바인드·뷰 생성·스택 커밋)
    │   ├── WidgetRepository.kt        위젯 ID 스택 영속화 + SurfaceRoute/SurfacePolicy 정책
    │   ├── WidgetOverlayApplication.kt  앱 진입점 + 24시간 유지보수 WorkManager
    │   └── ui/
    │       ├── Theme.kt               동적 색상 테마 컨텍스트/색상 역할 조회 (AppTheme)
    │       └── UiKit.kt               Design 토큰 + 공용 컴포넌트 팩토리 + wormIndicator
    └── app/src/test/                  SurfacePolicy 유닛 테스트
```

### 설계 원칙
- **위젯 데이터 미복제**: provider가 `RemoteViews`를 `AppWidgetHostView`에 공급하는 플랫폼 계약을 그대로 사용합니다. 앱은 위젯 ID만 보관합니다.
- **한 곳에서 호스팅**: `AppWidgetHost`(host id `0x574F564C`)의 생성·수신·삭제는 `WidgetHostController`만 수행합니다.
- **UI는 데이터를 모른다**: 표시 경로 결정은 순수 함수(`SurfacePolicy`)로 분리해 유닛 테스트로 보호합니다.
- **유지보수 최소화**: 24시간 주기 WorkManager가 저장된 위젯 ID의 유효성만 점검하고, 데이터를 폴링하지 않습니다.

### 오버레이 구현 노트
- **깜빡임 없는 전환**: 패널↔버블 전환은 종료 애니메이션 후 새 창을 먼저 추가하고 첫 프레임 이후 이전 창을 제거하는 원자적 교체로 동작합니다. 축소/확장은 버블 위치로 피벗을 맞춰 하나의 연속 동작처럼 보입니다.
- **절대 좌표 창**: 패널은 항상 `TOP|START` 절대 좌표 창입니다. 오버레이 창 좌표계는 상태바 아래에서 시작하므로(인셋트), 드래그 좌표 변환을 만들지 않아 오프셋 오류가 원천적으로 없습니다. 스크림은 전체 화면을 덮기 위해 명시적 크기 + 상태바 높이만큼의 음수 오프셋을 사용합니다.
- **인플레이스 새로고침**: 어댑터 교체 시에도 창을 재생성하지 않고, 로딩 오버레이로 입력을 잠시 차단한 뒤 보고 있던 페이지 인덱스를 복원합니다.

## 디자인 시스템

- **토큰**: `res/values/colors.xml`(라이트)과 `res/values-night/colors.xml`(다크)에 전체 Material 3 톤 팔레트(`surfaceContainerLow~Highest` 등)를 정의하고 `Theme.Material3.DayNight`에 매핑합니다.
- **동적 색상**: 액티비티는 `DynamicColors.applyToActivityIfAvailable`, 오버레이 서비스는 자체 테마에 동적 색상 오버레이를 인플레이스 적용합니다(`ui/Theme.kt`의 `AppTheme`이 색상 역할 조회를 단일화).
- **공용 컴포넌트** (`ui/UiKit.kt`): M3 shape/모션 상수, MaterialButton 스타일을 보존하는 버튼 팩토리(`backgroundTintList` 기반), 토널 원형 아이콘 버튼, 카드, edge-to-edge 인셋 헬퍼.
- **페이지 인디케이터**: [dotsindicator](https://github.com/tbuonomo/dotsindicator)의 `WormDotsIndicator`를 사용합니다(크기는 XML, 색상은 동적 색상 역할로 설정). 5.1.1은 compileSdk 37을 요구해 프로젝트(compileSdk 36)에 맞춘 **5.1.0**을 사용합니다.

## 빌드와 테스트

요구 환경: JDK 17, Android SDK Platform 36, Android Studio(권장).

```bash
# Gradle 루트는 app/ 디렉터리입니다
cd app

# Windows에서는 gradlew.bat 사용
./gradlew :app:assembleDebug        # 디버그 APK
./gradlew :app:testDebugUnitTest    # 유닛 테스트 (SurfacePolicy)
```

- APK는 `app/app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.
- SDK 경로는 `app/local.properties`에 설정합니다(git에 올라가지 않음). `app/local.properties.example` 참고.

### 실행 확인 시나리오
1. "위젯 추가" → 그리드에서 위젯 선택 → 바인드 승인. 설정이 필요한 위젯은 설정 화면 완료 후 돌아오면 추가됩니다.
2. 메인 미리보기에서 스와이프: 위젯 이름 라벨과 웜 인디케이터가 따라가는지 확인.
3. "오버레이 열기" → 안내에 따라 "다른 앱 위에 표시" 권한 허용 → 패널 + 스크림 표시.
4. 그랩 핸들 드래그로 패널 이동 → 최소화했다가 다시 열면 위치가 복원되는지 확인.
5. 스크림 탭/핸들 탭으로 최소화, 버블 탭으로 재오픈.
6. 새로고침 버튼: 로딩 표시 후 그 자리에서 콘텐츠 갱신, 보던 페이지 유지.

## 권한

| 권한 | 용도 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 다른 앱 위에 오버레이 표시. 시스템 설정의 특별 접근으로만 사용자가 직접 승인합니다. |
| `POST_NOTIFICATIONS` | 오버레이 실행 중 알림 표시(Android 13+ 런타임 요청). |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 오버레이가 열려 있는 동안의 전경 서비스 유지. |
| `QUERY_ALL_PACKAGES` | Android 12+ 패키지 가시성 필터링 환경에서 전체 위젯 제공자 목록 조회. |

이 앱은 인터넷·위치·연락처 등 다른 권한을 요청하지 않으며, 위젯 콘텐츠를 자체 저장소에 보관하지 않습니다.

## 알려진 한계

- **배경 블러 불가**: Android는 `TYPE_APPLICATION_OVERLAY` 창에서 배경(다른 앱) 블러를 허용하지 않으므로, 반투명 스크림 + 헤어라인 보더로 유사한 연출을 사용합니다. 시스템 상태바 자체 표면은 앱이 어둡게 할 수 없어 스크림 적용 후에도 상태바 스트립이 옅게 남을 수 있습니다(OEM별 차이).
- **타사 위젯 렌더링 다양성**: 제공자마다 크기·구성 Activity·컬렉션 UI·클릭 동작이 달라 모든 위젯이 동일한 모양으로 보이지는 않습니다.
- **오버레이 권한 철회**: 권한이 거부·철회되면 앱 내부 미리보기가 유일한 대체 경로입니다.
