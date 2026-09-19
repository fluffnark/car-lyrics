package com.doomslug.carlyrics

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class CarLyricsScreen(context: CarContext) : Screen(context), DefaultLifecycleObserver {
    private val surface = MirrorSurface()
    private val frameListener: () -> Unit = { surface.redraw() }
    private val stateListener: () -> Unit = { surface.redraw(); invalidate() }
    private var speedRegistered = false
    private val speedListener = OnCarDataAvailableListener<Speed> { speed ->
        val value = speed.rawSpeedMetersPerSecond
        val metersPerSecond = value.value
        if (value.status == CarValue.STATUS_SUCCESS && metersPerSecond != null) {
            val wasMoving = MirrorState.moving
            MirrorState.moving = metersPerSecond > 0.5f
            if (MirrorState.moving) MirrorState.carAuthorized = false
            if (wasMoving != MirrorState.moving) { surface.redraw(); invalidate() }
        }
    }

    init { lifecycle.addObserver(this) }

    override fun onStart(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surface)
        MirrorState.addFrameListener(frameListener)
        MirrorState.addStateListener(stateListener)
        speedRegistered = runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
                .addSpeedListener(carContext.mainExecutor, speedListener)
        }.isSuccess
    }

    override fun onStop(owner: LifecycleOwner) {
        MirrorState.carAuthorized = false
        MirrorState.removeFrameListener(frameListener)
        MirrorState.removeStateListener(stateListener)
        if (speedRegistered) runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(speedListener)
        }
        speedRegistered = false
        surface.redraw()
    }

    override fun onDestroy(owner: LifecycleOwner) { surface.close() }

    override fun onGetTemplate(): Template {
        val rows = ItemList.Builder()
        val state = when {
            MirrorState.moving -> "Video hidden while moving"
            !MirrorState.capturing -> "Start sharing from the phone"
            MirrorState.carAuthorized -> "Morphe video is shown"
            else -> "Ready to show video while parked"
        }
        rows.addItem(Row.Builder().setTitle(state).build())
        rows.addItem(Row.Builder().setTitle("Show video")
            .setOnClickListener(ParkedOnlyOnClickListener.create {
                if (MirrorState.capturing && !MirrorState.moving) {
                    MirrorState.carAuthorized = true
                    surface.redraw()
                    invalidate()
                }
            }).build())
        rows.addItem(Row.Builder().setTitle("Hide video").setOnClickListener {
            MirrorState.carAuthorized = false
            surface.redraw()
            invalidate()
        }.build())
        rows.addItem(Row.Builder().setTitle("Stop sharing").setOnClickListener {
            carContext.startService(Intent(carContext, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
            MirrorState.carAuthorized = false
            surface.redraw()
            invalidate()
        }.build())
        return MapWithContentTemplate.Builder()
            .setContentTemplate(ListTemplate.Builder().setSingleList(rows.build()).build())
            .setActionStrip(androidx.car.app.model.ActionStrip.Builder()
                .addAction(Action.Builder().setTitle("Hide").setOnClickListener {
                    MirrorState.carAuthorized = false
                    surface.redraw()
                    invalidate()
                }.build()).build())
            .build()
    }
}
