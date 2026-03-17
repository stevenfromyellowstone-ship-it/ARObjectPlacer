package com.arobjectplacer

import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arobjectplacer.databinding.ActivityArPlacementBinding
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ARPlacementActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    private lateinit var binding: ActivityArPlacementBinding
    private lateinit var objectStore: ObjectStore
    private var arSession: Session? = null
    private var capturedObject: CapturedObject? = null
    private var objectTextureBitmap: Bitmap? = null
    private var bgTextureId = -1
    private var objTextureId = -1
    private var bgProgram = 0; private var objProgram = 0
    private lateinit var quadVerts: FloatBuffer; private lateinit var quadTexCoords: FloatBuffer
    private lateinit var billboardVerts: FloatBuffer; private lateinit var billboardTexCoords: FloatBuffer
    private lateinit var billboardIndices: ShortBuffer
    private var needsTextureUpload = false

    data class PlacedObj(val anchor: Anchor, var rotY: Float = 0f, var scale: Float = 1f,
                         val w: Float, val h: Float, val d: Float)

    private val placed = mutableListOf<PlacedObj>()
    private var selectedIdx = -1; private var curRot = 0f; private var curScale = 1f
    private val viewMat = FloatArray(16); private val projMat = FloatArray(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArPlacementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        objectStore = (application as ARObjectPlacerApp).objectStore

        val id = intent.getLongExtra("object_id", -1)
        if (id == -1L) { finish(); return }
        lifecycleScope.launch {
            capturedObject = objectStore.getObject(id)
            capturedObject?.let { obj ->
                objectTextureBitmap = withContext(Dispatchers.IO) { objectStore.loadObjectTexture(obj) }
                needsTextureUpload = true
                binding.tvObjectName.text = obj.name
                binding.tvObjectInfo.text = String.format("%.0f×%.0f×%.0f cm", obj.widthMeters*100, obj.heightMeters*100, obj.depthMeters*100)
            } ?: finish()
        }

        binding.glSurfaceView.apply {
            preserveEGLContextOnPause = true; setEGLContextClientVersion(2)
            setEGLConfigChooser(8,8,8,8,16,0)
            setRenderer(this@ARPlacementActivity); renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnClearAll.setOnClickListener { placed.forEach { it.anchor.detach() }; placed.clear(); selectedIdx = -1; updateUI() }
        binding.btnDelete.setOnClickListener {
            if (selectedIdx in placed.indices) { placed[selectedIdx].anchor.detach(); placed.removeAt(selectedIdx); selectedIdx = -1; updateUI() }
        }
        binding.seekBarRotation.setOnSeekBarChangeListener(seekListener { p ->
            curRot = p.toFloat(); if (selectedIdx in placed.indices) placed[selectedIdx].rotY = curRot
            binding.tvRotation.text = "${curRot.toInt()}°"
        })
        binding.seekBarScale.setOnSeekBarChangeListener(seekListener { p ->
            curScale = 0.25f + (p/100f)*3.75f; if (selectedIdx in placed.indices) placed[selectedIdx].scale = curScale
            binding.tvScale.text = String.format("%.1fx", curScale)
        })
        binding.seekBarScale.progress = 25
        binding.glSurfaceView.setOnTouchListener { _, ev -> if (ev.action == MotionEvent.ACTION_DOWN) handleTap(ev.x, ev.y); true }
    }

    private fun handleTap(x: Float, y: Float) {
        val session = arSession ?: return; val obj = capturedObject ?: return
        val frame = session.update()
        frame.hitTest(x, y).firstOrNull { (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true }?.let { hit ->
            placed.add(PlacedObj(hit.createAnchor(), curRot, curScale, obj.widthMeters, obj.heightMeters, obj.depthMeters))
            selectedIdx = placed.size - 1
            runOnUiThread { updateUI() }
        }
    }

    private fun updateUI() {
        binding.tvPlacedCount.text = "Objects: ${placed.size}"; binding.btnDelete.isEnabled = selectedIdx >= 0
        binding.tvInstructions.visibility = if (placed.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun seekListener(action: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { action(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    // ========== GL Renderer ==========
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        val texIds = IntArray(2); GLES20.glGenTextures(2, texIds, 0)
        bgTextureId = texIds[0]; objTextureId = texIds[1]

        // BG quad
        val qc = floatArrayOf(-1f,-1f,0f, -1f,1f,0f, 1f,-1f,0f, 1f,1f,0f)
        val qt = floatArrayOf(0f,1f, 0f,0f, 1f,1f, 1f,0f)
        quadVerts = makeFB(qc); quadTexCoords = makeFB(qt)
        bgProgram = makeProgram(BG_VS, BG_FS)

        // Billboard
        val bv = floatArrayOf(-0.5f,0f,0f, 0.5f,0f,0f, 0.5f,1f,0f, -0.5f,1f,0f)
        val bt = floatArrayOf(0f,1f, 1f,1f, 1f,0f, 0f,0f)
        val bi = shortArrayOf(0,1,2, 0,2,3)
        billboardVerts = makeFB(bv); billboardTexCoords = makeFB(bt)
        billboardIndices = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(bi); position(0) }
        objProgram = makeProgram(OBJ_VS, OBJ_FS)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, objTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        // Upload placeholder
        val placeholder = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.argb(200, 76, 175, 80))
        }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, placeholder, 0); placeholder.recycle()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        arSession?.setDisplayGeometry(windowManager.defaultDisplay.rotation, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = arSession ?: return

        if (needsTextureUpload && objectTextureBitmap != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, objTextureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, objectTextureBitmap, 0)
            needsTextureUpload = false
        }

        try {
            session.setCameraTextureName(bgTextureId)
            val frame = session.update(); val cam = frame.camera
            drawBackground(frame)
            if (cam.trackingState != TrackingState.TRACKING) return
            cam.getProjectionMatrix(projMat, 0, 0.1f, 100f); cam.getViewMatrix(viewMat, 0)

            for (p in placed) {
                if (p.anchor.trackingState != TrackingState.TRACKING) continue
                val modelMat = FloatArray(16); p.anchor.pose.toMatrix(modelMat, 0)
                val rotMat = FloatArray(16); Matrix.setRotateM(rotMat, 0, p.rotY, 0f, 1f, 0f)
                val rm = FloatArray(16); Matrix.multiplyMM(rm, 0, modelMat, 0, rotMat, 0)
                Matrix.scaleM(rm, 0, p.w * p.scale, p.h * p.scale, p.d * p.scale)
                drawObject(rm)
            }
        } catch (e: CameraNotAvailableException) { Log.e("AR", "Camera unavailable", e) }
    }

    private fun drawBackground(frame: Frame) {
        if (frame.hasDisplayGeometryChanged())
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quadVerts, Coordinates2d.TEXTURE_NORMALIZED, quadTexCoords)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDepthMask(false)
        GLES20.glUseProgram(bgProgram)
        GLES20.glBindTexture(0x8D65, bgTextureId)
        val posA = GLES20.glGetAttribLocation(bgProgram, "a_Position")
        val texA = GLES20.glGetAttribLocation(bgProgram, "a_TexCoord")
        GLES20.glVertexAttribPointer(posA, 3, GLES20.GL_FLOAT, false, 0, quadVerts)
        GLES20.glVertexAttribPointer(texA, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)
        GLES20.glEnableVertexAttribArray(posA); GLES20.glEnableVertexAttribArray(texA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posA); GLES20.glDisableVertexAttribArray(texA)
        GLES20.glDepthMask(true); GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawObject(modelMat: FloatArray) {
        val mv = FloatArray(16); val mvp = FloatArray(16)
        Matrix.multiplyMM(mv, 0, viewMat, 0, modelMat, 0)
        Matrix.multiplyMM(mvp, 0, projMat, 0, mv, 0)
        GLES20.glUseProgram(objProgram); GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, objTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(objProgram, "u_Texture"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(objProgram, "u_MVPMatrix"), 1, false, mvp, 0)
        val posA = GLES20.glGetAttribLocation(objProgram, "a_Position")
        val texA = GLES20.glGetAttribLocation(objProgram, "a_TexCoord")
        GLES20.glVertexAttribPointer(posA, 3, GLES20.GL_FLOAT, false, 0, billboardVerts)
        GLES20.glVertexAttribPointer(texA, 2, GLES20.GL_FLOAT, false, 0, billboardTexCoords)
        GLES20.glEnableVertexAttribArray(posA); GLES20.glEnableVertexAttribArray(texA)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, billboardIndices)
        GLES20.glDisableVertexAttribArray(posA); GLES20.glDisableVertexAttribArray(texA)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    // ========== AR Lifecycle ==========
    override fun onResume() {
        super.onResume()
        if (arSession == null) {
            try {
                if (ArCoreApk.getInstance().requestInstall(this, true) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) return
                arSession = Session(this).apply {
                    configure(Config(this).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        if (isDepthModeSupported(Config.DepthMode.AUTOMATIC)) depthMode = Config.DepthMode.AUTOMATIC
                        focusMode = Config.FocusMode.AUTO
                    })
                }
            } catch (e: Exception) { Toast.makeText(this, "ARCore error: ${e.message}", Toast.LENGTH_LONG).show(); finish(); return }
        }
        try { arSession?.resume() } catch (e: CameraNotAvailableException) { arSession = null; finish(); return }
        binding.glSurfaceView.onResume()
    }
    override fun onPause() { super.onPause(); arSession?.pause(); binding.glSurfaceView.onPause() }
    override fun onDestroy() { super.onDestroy(); arSession?.close(); objectTextureBitmap?.recycle() }

    // ========== GL Helpers ==========
    private fun makeFB(a: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(a.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }
    private fun makeProgram(vs: String, fs: String): Int {
        fun load(t: Int, s: String) = GLES20.glCreateShader(t).also { GLES20.glShaderSource(it, s); GLES20.glCompileShader(it) }
        return GLES20.glCreateProgram().also { GLES20.glAttachShader(it, load(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(it, load(GLES20.GL_FRAGMENT_SHADER, fs)); GLES20.glLinkProgram(it) }
    }

    companion object {
        private const val BG_VS = "attribute vec4 a_Position;attribute vec2 a_TexCoord;varying vec2 v_TexCoord;void main(){gl_Position=a_Position;v_TexCoord=a_TexCoord;}"
        private const val BG_FS = "#extension GL_OES_EGL_image_external:require\nprecision mediump float;varying vec2 v_TexCoord;uniform samplerExternalOES sTexture;void main(){gl_FragColor=texture2D(sTexture,v_TexCoord);}"
        private const val OBJ_VS = "uniform mat4 u_MVPMatrix;attribute vec4 a_Position;attribute vec2 a_TexCoord;varying vec2 v_TexCoord;void main(){v_TexCoord=a_TexCoord;gl_Position=u_MVPMatrix*a_Position;}"
        private const val OBJ_FS = "precision mediump float;uniform sampler2D u_Texture;varying vec2 v_TexCoord;void main(){vec4 c=texture2D(u_Texture,v_TexCoord);if(c.a<0.1)discard;gl_FragColor=c;}"
    }
}
