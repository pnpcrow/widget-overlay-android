package com.example.widgetoverlay

import org.junit.Assert.assertEquals
import org.junit.Test

class SurfacePolicyTest {
    @Test
    fun widgetUsesOverlayOnlyWhenWidgetAndPermissionExist() {
        assertEquals(
            SurfaceRoute.OVERLAY_WIDGET,
            SurfacePolicy.widgetRoute(hasWidget = true, overlayPermissionGranted = true),
        )
        assertEquals(
            SurfaceRoute.IN_APP_WIDGET,
            SurfacePolicy.widgetRoute(hasWidget = true, overlayPermissionGranted = false),
        )
        assertEquals(
            SurfaceRoute.NONE,
            SurfacePolicy.widgetRoute(hasWidget = false, overlayPermissionGranted = true),
        )
    }

    @Test
    fun bubbleRequiresExplicitUserRequestAndNotificationAccess() {
        assertEquals(
            SurfaceRoute.BUBBLE_NOTIFICATION,
            SurfacePolicy.bubbleRoute(userRequested = true, notificationsAllowed = true),
        )
        assertEquals(
            SurfaceRoute.NONE,
            SurfacePolicy.bubbleRoute(userRequested = false, notificationsAllowed = true),
        )
        assertEquals(
            SurfaceRoute.NONE,
            SurfacePolicy.bubbleRoute(userRequested = true, notificationsAllowed = false),
        )
    }

    @Test
    fun liveUpdateRequiresApi36UserJourneyAndNotifications() {
        assertEquals(
            SurfaceRoute.LIVE_UPDATE_NOTIFICATION,
            SurfacePolicy.liveUpdateRoute(apiLevel = 36, userStartedJourney = true, notificationsAllowed = true),
        )
        assertEquals(
            SurfaceRoute.NONE,
            SurfacePolicy.liveUpdateRoute(apiLevel = 35, userStartedJourney = true, notificationsAllowed = true),
        )
        assertEquals(
            SurfaceRoute.NONE,
            SurfacePolicy.liveUpdateRoute(apiLevel = 36, userStartedJourney = false, notificationsAllowed = true),
        )
        assertEquals(
            SurfaceRoute.NONE,
            SurfacePolicy.liveUpdateRoute(apiLevel = 36, userStartedJourney = true, notificationsAllowed = false),
        )
    }
}

