package com.doomslug.carlyrics

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.car.app.SurfaceContainer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A permanent capture input; only the EGL output changes when the car replaces its surface. */
internal class MorpheVideoRenderer(width: Int, height: Int) {
    private val thread = HandlerThread("Morphe video").apply { start() }
    private val handler = Handler(thread.looper)
    private var egl = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private lateinit var config: EGLConfig
    private var parking = EGL14.EGL_NO_SURFACE
    private var output: EGLSurface = EGL14.EGL_NO_SURFACE
    private lateinit var texture: SurfaceTexture
    lateinit var input: Surface
        private set
    private var textureId = 0
    private var program = 0
    private var width = width
    private var height = height
    private var outputWidth = 0
    private var outputHeight = 0
    private var hasFrame = false
    private var frames = 0
    private val matrix = FloatArray(16)
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)); position(0) }

    init {
        val ready = CountDownLatch(1)
        var failure: Throwable? = null
        handler.post {
            try { initialize() } catch (e: Throwable) { failure = e } finally { ready.countDown() }
        }
        check(ready.await(5, TimeUnit.SECONDS)) { "Video renderer initialization timed out" }
        failure?.let { close(); throw IllegalStateException("Video renderer unavailable", it) }
    }

    private fun initialize() {
        egl = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(egl, null, 0, null, 0))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(egl, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0))
        config = checkNotNull(configs[0])
        context = EGL14.eglCreateContext(egl, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT)
        parking = EGL14.eglCreatePbufferSurface(egl, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        current(parking)
        val names = IntArray(1)
        GLES20.glGenTextures(1, names, 0)
        textureId = names[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        program = GLES20.glCreateProgram()
        val vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec4 position; attribute vec4 uv; uniform mat4 transform; varying vec2 tex; void main(){gl_Position=position; tex=(transform*uv).xy;}")
        val fragment = shader(GLES20.GL_FRAGMENT_SHADER, "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES image; varying vec2 tex; void main(){gl_FragColor=texture2D(image,tex);}")
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { GLES20.glGetProgramInfoLog(program) }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        texture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener({
                runCatching {
                    current(if (output != EGL14.EGL_NO_SURFACE) output else parking)
                    updateTexImage()
                    getTransformMatrix(matrix)
                    hasFrame = true
                    draw()
                    if (++frames % 300 == 1) Log.i(TAG, "captured frame=$frames output=${output != EGL14.EGL_NO_SURFACE}")
                }.onFailure { Log.w(TAG, "Video frame failed", it); detachOutput() }
            }, handler)
        }
        input = Surface(texture)
    }

    fun resize(width: Int, height: Int) { handler.post {
        this.width = width
        this.height = height
        texture.setDefaultBufferSize(width, height)
    } }

    fun attach(container: SurfaceContainer) { handler.post {
        detachOutput()
        val surface = container.surface ?: return@post
        if (!surface.isValid || container.width <= 0 || container.height <= 0) return@post
        runCatching {
            outputWidth = container.width
            outputHeight = container.height
            output = EGL14.eglCreateWindowSurface(egl, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            check(output != EGL14.EGL_NO_SURFACE) { "Car output error ${EGL14.eglGetError()}" }
            current(output)
            draw()
        }.onFailure { Log.e(TAG, "Video output failed", it); detachOutput() }
    } }

    fun detach() { handler.post { detachOutput() } }
    private fun detachOutput() {
        current(parking)
        if (output != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(egl, output)
        output = EGL14.EGL_NO_SURFACE
    }
    private fun current(surface: EGLSurface) {
        check(EGL14.eglMakeCurrent(egl, surface, surface, context)) { "EGL context error ${EGL14.eglGetError()}" }
    }
    private fun draw() {
        if (output == EGL14.EGL_NO_SURFACE || !hasFrame) return
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val scale = minOf(outputWidth.toFloat() / width, outputHeight.toFloat() / height)
        val w = (width * scale).toInt()
        val h = (height * scale).toInt()
        GLES20.glViewport((outputWidth-w)/2, (outputHeight-h)/2, w, h)
        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "position")
        val uv = GLES20.glGetAttribLocation(program, "uv")
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
        vertices.position(2)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "image"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "transform"), 1, false, matrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        check(EGL14.eglSwapBuffers(egl, output)) { "Car display disconnected" }
    }
    private fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] != 0) { GLES20.glGetShaderInfoLog(id) }
        return id
    }
    fun close() { handler.post {
        runCatching {
            if (::texture.isInitialized) { texture.setOnFrameAvailableListener(null); input.release(); texture.release() }
            detachOutput()
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            EGL14.eglMakeCurrent(egl, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(egl, parking)
            EGL14.eglDestroyContext(egl, context)
            EGL14.eglTerminate(egl)
            EGL14.eglReleaseThread()
        }
        thread.quitSafely()
    } }
    companion object { private const val TAG = "CarLyricsMorphe" }
}
