/*
 * Copyright (c) 2024, codex
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package codex.renthyl;

import codex.renthyl.resources.ResourceList;
import codex.renthyl.util.FullScreenQuad;
import codex.renthyl.debug.GraphEventCapture;
import com.jme3.light.LightFilter;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.opencl.CommandQueue;
import com.jme3.opencl.Context;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Texture2D;
import java.util.function.Predicate;
import com.jme3.renderer.GeometryRenderHandler;

/**
 * Context for FrameGraph rendering.
 * <p>
 * Provides RenderPasses with access to important objects such as the RenderManager,
 * ViewPort, profiler, and fullscreen quad. Utility methods are provided for
 * fullscreen quad rendering and camera management.
 * <p>
 * Additionally, the following render settings are handled to ensure settings
 * do not leak between renders.
 * <ul>
 *   <li>Forced technique</li>
 *   <li>Forced material</li>
 *   <li>Geometry render handler</li>
 *   <li>Geometry filter</li>
 *   <li>Forced render state</li>
 * </ul>
 * After each pass execution on the main render thread, {@link #popRenderSettings()} is
 * called to reset these settings to what they were before rendering began.
 * 
 * @author codex
 */
public class FGRenderContext {
    
    private final FrameGraph frameGraph;
    private RenderManager renderManager;
    private FGPipelineContext context;
    private ViewPort viewPort;
    private float tpf;
    private final FullScreenQuad screen;
    private Context clContext;
    private CommandQueue clQueue;
    
    private String forcedTechnique;
    private Material forcedMat;
    private FrameBuffer frameBuffer;
    private GeometryRenderHandler geomRender;
    private Predicate<Geometry> geomFilter;
    private RenderState renderState;
    private LightFilter lightFilter;
    private int camWidth, camHeight;
    
    public FGRenderContext(FrameGraph frameGraph) {
        this(frameGraph, null);
    }
    public FGRenderContext(FrameGraph frameGraph, Context clContext) {
        this.frameGraph = frameGraph;
        this.clContext = clContext;
        this.screen = new FullScreenQuad(this.frameGraph.getAssetManager());
    }
    
    /**
     * Targets this context to the viewport.
     * 
     * @param rm
     * @param context
     * @param vp
     * @param tpf 
     */
    public void target(RenderManager rm, FGPipelineContext context, ViewPort vp, float tpf) {
        this.renderManager = rm;
        this.context = context;
        this.viewPort = vp;
        this.tpf = tpf;
        if (viewPort == null) {
            throw new NullPointerException("ViewPort cannot be null.");
        }
    }
    /**
     * Returns true if the context is ready for rendering.
     * 
     * @return 
     */
    public boolean isReady() {
        return renderManager != null && viewPort != null;
    }
    
    /**
     * Saves the current render settings.
     */
    public void pushRenderSettings() {
        forcedTechnique = renderManager.getForcedTechnique();
        forcedMat = renderManager.getForcedMaterial();
        frameBuffer = renderManager.getRenderer().getCurrentFrameBuffer();
        geomRender = renderManager.getGeometryRenderHandler();
        geomFilter = renderManager.getRenderFilter();
        renderState = renderManager.getForcedRenderState();
        lightFilter = renderManager.getLightFilter();
        camWidth = viewPort.getCamera().getWidth();
        camHeight = viewPort.getCamera().getHeight();
    }
    /**
     * Applies saved render settings, except the framebuffer.
     */
    public void popRenderSettings() {
        renderManager.setForcedTechnique(forcedTechnique);
        renderManager.setForcedMaterial(forcedMat);
        renderManager.getRenderer().setFrameBuffer(frameBuffer);
        renderManager.setGeometryRenderHandler(geomRender);
        renderManager.setRenderFilter(geomFilter);
        renderManager.setForcedRenderState(renderState);
        renderManager.getRenderer().setDepthRange(0, 1);
        renderManager.setLightFilter(lightFilter);
        resizeCamera(camWidth, camHeight, true, false, false);
        if (renderManager.getCurrentCamera() != viewPort.getCamera()) {
            renderManager.setCamera(viewPort.getCamera(), false);
        }
        if (viewPort.isClearColor()) {
            renderManager.getRenderer().setBackgroundColor(viewPort.getBackgroundColor());
        }
    }
    /**
     * Applies the saved framebuffer.
     */
    public void popFrameBuffer() {
        renderManager.getRenderer().setFrameBuffer(frameBuffer);
    }
    
    /**
     * Renders the given geometry list with the camera and render handler.
     * 
     * @param queue queue of geometry to render (not null)
     * @param queueSortCam camera to sort geometry by (or null to use viewport camera is used)
     * @param handler handler to render with (or null to render with {@link GeometryRenderHandler#DEFAULT})
     */
    public void renderGeometry(GeometryQueue queue, Camera queueSortCam, GeometryRenderHandler handler) {
        if (queueSortCam == null) {
            queueSortCam = viewPort.getCamera();
        }
        queue.setCamera(queueSortCam);
        queue.sort();
        queue.render(renderManager, handler);
    }
    /**
     * Renders the material on a fullscreen quad.
     * 
     * @param mat 
     */
    public void renderFullscreen(Material mat) {
        screen.render(renderManager, mat);
    }
    /**
     * Renders the color and depth textures on a fullscreen quad, where
     * the color texture informs the color, and the depth texture informs
     * the depth.
     * <p>
     * If both color and depth are null, no rendering will be performed
     * 
     * @param color color texture, or null
     * @param depth depth texture, or null
     */
    public void renderTextures(Texture2D color, Texture2D depth) {
        if (color != null) {
            //resizeCamera(color.getImage().getWidth(), color.getImage().getHeight(), false, false);
        } else if (depth != null) {
            //resizeCamera(depth.getImage().getWidth(), depth.getImage().getHeight(), false, false);
        }
        screen.render(renderManager, color, depth);
    }
    
    /**
     * Resizes the camera to the width and height.
     * 
     * @param w new camera width
     * @param h new camera height
     * @param fixAspect true to fix camera aspect
     * @param ortho true to use parallel projection
     * @param force true to force setting the width and height
     */
    public void resizeCamera(int w, int h, boolean fixAspect, boolean ortho, boolean force) {
        Camera cam = viewPort.getCamera();
        if (force || w != cam.getWidth() || h != cam.getHeight()) {
            cam.resize(w, h, fixAspect);
            renderManager.setCamera(cam, ortho);
        }
    }
    
    public void setCamera(Camera cam, boolean ortho, boolean force) {
        if (force || renderManager.getCurrentCamera() != cam) {
            renderManager.setCamera(cam, ortho);
        }
    }
    
    /**
     * Sets the OpenCL context for compute shading.
     * 
     * @param clContext 
     */
    public void setCLContext(Context clContext) {
        this.clContext = clContext;
    }
    /**
     * Sets the OpenCL command queue for compute shading.
     * 
     * @param clQueue 
     */
    public void setCLQueue(CommandQueue clQueue) {
        this.clQueue = clQueue;
    }
    
    /**
     * Gets the resource list belonging to the framegraph.
     * 
     * @return 
     */
    public ResourceList getResources() {
        return frameGraph.getResources();
    }
    /**
     * Gets the render manager.
     * 
     * @return 
     */
    public RenderManager getRenderManager() {
        return renderManager;
    }
    /**
     * Gets the context for the FrameGraph pipeline.
     * 
     * @return 
     */
    public FGPipelineContext getPipelineContext() {
        return context;
    }
    /**
     * Gets the viewport currently being rendered.
     * 
     * @return 
     */
    public ViewPort getViewPort() {
        return viewPort;
    }
    /**
     * Gets the profiler.
     * 
     * @return app profiler, or null
     */
    public AppProfiler getProfiler() {
        return renderManager.getProfiler();
    }
    /**
     * Gets the renderer held by the render manager.
     * 
     * @return 
     */
    public Renderer getRenderer() {
        return renderManager.getRenderer();
    }
    /**
     * Gets the render queue held by the viewport.
     * 
     * @return 
     */
    public RenderQueue getRenderQueue() {
        if (viewPort != null) {
            return viewPort.getQueue();
        } else {
            return null;
        }
    }
    /**
     * Gets the fullscreen quad used for fullscreen renders.
     * 
     * @return 
     */
    public FullScreenQuad getScreen() {
        return screen;
    }
    /**
     * Gets the debug frame capture if one is assigned.
     * 
     * @return 
     */
    public GraphEventCapture getGraphCapture() {
        return context.getEventCapture();
    }
    /**
     * Gets the OpenCL context for compute shading.
     * 
     * @return 
     */
    public Context getCLContext() {
        return clContext;
    }
    /**
     * Gets the OpenCL command queue assigned to this context.
     * 
     * @return 
     */
    public CommandQueue getCLQueue() {
        return clQueue;
    }
    /**
     * Gets the time per frame.
     * 
     * @return 
     */
    public float getTpf() {
        return tpf;
    }
    /**
     * Gets the camera width.
     * 
     * @return 
     */
    public int getWidth() {
        return viewPort.getCamera().getWidth();
    }
    /**
     * Gets the camera height.
     * 
     * @return 
     */
    public int getHeight() {
        return viewPort.getCamera().getHeight();
    }
    /**
     * Returns true if the FrameGraph is asynchronous.
     * 
     * @return 
     */
    public boolean isAsync() {
        return frameGraph.isAsync();
    }
    
    /**
     * Returns true if the app profiler is not null.
     * 
     * @return 
     */
    public boolean isProfilerAvailable() {
        return renderManager.getProfiler() != null;
    }
    /**
     * Returns true if a debug frame snapshot is assigned.
     * 
     * @return 
     */
    public boolean isGraphCaptureActive() {
        return context.getEventCapture() != null;
    }
    
}
