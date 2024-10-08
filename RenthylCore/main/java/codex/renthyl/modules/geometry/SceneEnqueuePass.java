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
package codex.renthyl.modules.geometry;

import codex.boost.export.SavableObject;
import codex.renthyl.modules.RenderPass;
import codex.renthyl.FGRenderContext;
import codex.renthyl.FrameGraph;
import codex.renthyl.GeometryQueue;
import codex.renthyl.resources.ResourceTicket;
import codex.boost.render.DepthRange;
import codex.renthyl.util.SpatialWorldParam;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.light.Light;
import com.jme3.light.LightList;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryComparator;
import com.jme3.renderer.queue.GuiComparator;
import com.jme3.renderer.queue.NullComparator;
import com.jme3.renderer.queue.OpaqueComparator;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.TransparentComparator;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Enqueues geometries into different {@link GeometryQueue}s based on world
 * render bucket value.
 * <p>
 * Outputs vary based on what GeometryQueues are added. If default queues are
 * added (via {@link #SceneEnqueuePass(boolean, boolean)}), then the outputs
 * include: "Opaque", "Sky", "Transparent", "Gui", and "Translucent". All outputs
 * are GeometryQueues.
 * <p>
 * A geometry is placed in queues according to the userdata found at
 * {@link #QUEUE} (expected as String) according to ancestor inheritance, or the
 * value returned by {@link Geometry#getQueueBucket()} (converted to String).
 * Userdata value (if found) trumps queue bucket value.
 * 
 * @author codex
 */
public class SceneEnqueuePass extends RenderPass {
    
    /**
     * Userdata key for denoting the queue the spatial should be sorted into.
     */
    public static final String QUEUE = "SceneEnqueuePass.RenderQueue";
    
    /**
     * Userdata value for inheriting the queue of the spatial's parent.
     */
    public static final String INHERIT = RenderQueue.Bucket.Inherit.name();
    
    public static final String
            OPAQUE = "Opaque",
            SKY = "Sky",
            TRANSPARENT = "Transparent",
            GUI = "Gui",
            TRANSLUCENT = "Translucent";
    
    private boolean runControlRender = true;
    private final HashMap<String, Queue> queues = new HashMap<>();
    private final LinkedList<SpatialWorldParam> worldParams = new LinkedList<>();
    private String defaultBucket = OPAQUE;

    /**
     * Initialize an instance with default settings.
     * <p>
     * Default queues are not added.
     */
    public SceneEnqueuePass() {
        this(true, true);
    }
    /**
     * 
     * @param runControlRender true to have this pass run {@link com.jme3.scene.control.Control} renders
     * @param useDefaultBuckets true to have default queues registered
     */
    public SceneEnqueuePass(boolean runControlRender, boolean useDefaultBuckets) {
        this.runControlRender = runControlRender;
        if (useDefaultBuckets) {
            add(OPAQUE, new OpaqueComparator());
            add(SKY, null, DepthRange.REAR, true);
            add(TRANSPARENT, new TransparentComparator());
            add(GUI, new GuiComparator(), DepthRange.FRONT, false);
            add(TRANSLUCENT, new TransparentComparator());
        }
        worldParams.add(SpatialWorldParam.RenderQueueParam);
    }
    
    @Override
    protected void initialize(FrameGraph frameGraph) {
        for (Queue b : queues.values()) {
            b.geometry = addOutput(b.name);
            b.lights = addOutput(b.name+"Lights");
        }
    }
    @Override
    protected void prepare(FGRenderContext context) {
        for (Queue b : queues.values()) {
            declare(null, b.geometry);
            declare(null, b.lights);
        }
    }
    @Override
    protected void execute(FGRenderContext context) {
        ViewPort vp = context.getViewPort();
        List<Spatial> scenes = vp.getScenes();
        for (int i = scenes.size()-1; i >= 0; i--) {
            vp.getCamera().setPlaneState(0);
            queueSubScene(context, scenes.get(i));
        }
        for (Queue b : queues.values()) {
            resources.setPrimitive(b.geometry, b.queue);
            resources.setPrimitive(b.lights, b.lightList);
        }
    }
    @Override
    protected void reset(FGRenderContext context) {
        for (Queue b : queues.values()) {
            b.queue.clear();
            b.lightList.clear();
        }
    }
    @Override
    protected void cleanup(FrameGraph frameGraph) {}
    @Override
    public void write(JmeExporter ex) throws IOException {
        super.write(ex);
        OutputCapsule out = ex.getCapsule(this);
        out.write(runControlRender, "runControlRender", true);
        ArrayList<Queue> list = new ArrayList<>();
        list.addAll(queues.values());
        out.writeSavableArrayList(list, "buckets", new ArrayList<>());
        out.write(defaultBucket, "defaultBucket", OPAQUE);
    }
    @Override
    public void read(JmeImporter im) throws IOException {
        super.read(im);
        InputCapsule in = im.getCapsule(this);
        runControlRender = in.readBoolean("runControlRender", true);
        ArrayList<Savable> list = in.readSavableArrayList("buckets", new ArrayList<>());
        for (Savable s : list) {
            Queue b = (Queue)s;
            queues.put(b.name, b);
        }
        defaultBucket = in.readString("defaultBucket", OPAQUE);
    }
    
    private void queueSubScene(FGRenderContext context, Spatial spatial) {
        // check culling
        Camera cam = context.getViewPort().getCamera();
        if (!spatial.checkCulling(cam)) {
            return;
        }
        // render controls
        if (runControlRender) {
            spatial.runControlRender(context.getRenderManager(), context.getViewPort());
        }
        // apply world parameters
        for (SpatialWorldParam p : worldParams) {
            p.apply(spatial);
        }
        // get target bucket
        String value = SpatialWorldParam.RenderQueueParam.getWorldValue(spatial);
        if (value == null) {
            throw new NullPointerException("World render queue value was not calculated correctly.");
        }
        Queue queue = queues.get(value);
        // accumulate lights
        if (queue != null) for (Light l : spatial.getLocalLightList()) {
            queue.lightList.add(l);
        }
        if (spatial instanceof Node) {
            int camState = cam.getPlaneState();
            for (Spatial s : ((Node)spatial).getChildren()) {
                // restore cam state before queueing children
                cam.setPlaneState(camState);
                queueSubScene(context, s);
            }
        } else if (queue != null && spatial instanceof Geometry) {
            // add to the render queue
            Geometry g = (Geometry)spatial;
            if (g.getMaterial() == null) {
                throw new IllegalStateException("No material is set for Geometry: " + g.getName());
            }
            queue.queue.add(g);
        }
    }
    
    /**
     * Adds a queue with the name and comparator.
     * <p>
     * If a bucket already exists under the name, it will be replaced.
     * 
     * @param name name of the queue corresponding to the output name
     * @param comparator sorts geometries within the queue
     * @return this instance
     * @throws IllegalStateException if called while assigned to a framegraph
     */
    public final SceneEnqueuePass add(String name, GeometryComparator comparator) {
        return add(name, comparator, DepthRange.NORMAL, true);
    }
    /**
     * Adds a queue with the name, comparator, depth range, and perspective mode.
     * 
     * @param name name of the queue corresponding to the output name.
     * @param comparator sorts geometries within the queue
     * @param depth range in which geometries in the bucket will be rendered within
     * @param perspective true to render geometries in the bucket in perspective mode (versus orthogonal)
     * @return this instance
     * @throws IllegalStateException if called while assigned to a framegraph
     */
    public final SceneEnqueuePass add(String name, GeometryComparator comparator, DepthRange depth, boolean perspective) {
        if (isAssigned()) {
            throw new IllegalStateException("Cannot add buckets while assigned to a framegraph.");
        }
        queues.put(name, new Queue(name, comparator, depth, perspective));
        return this;
    }
    
    /**
     * 
     * @param param 
     */
    public void addWorldParam(SpatialWorldParam param) {
        worldParams.add(param);
    }
    
    /**
     * Sets this pass to render controls when traversing the scene.
     * <p>
     * default=true
     * 
     * @param runControlRender 
     */
    public void setRunControlRender(boolean runControlRender) {
        this.runControlRender = runControlRender;
    }
    /**
     * Sets the default bucket geometries are added to if their
     * hierarchy only calls for {@link #INHERIT}.
     * <p>
     * default={@link #OPAQUE}
     * 
     * @param defaultBucket 
     */
    public void setDefaultBucket(String defaultBucket) {
        this.defaultBucket = defaultBucket;
    }

    /**
     * 
     * @return 
     */
    public boolean isRunControlRender() {
        return runControlRender;
    }
    /**
     * 
     * @return 
     */
    public String getDefaultBucket() {
        return defaultBucket;
    }
    
    private static class Queue implements Savable {
        
        public static final NullComparator NULL_COMPARATOR = new NullComparator();
        
        public String name;
        public GeometryQueue queue;
        public final LightList lightList = new LightList(null);
        public ResourceTicket<GeometryQueue> geometry;
        public ResourceTicket<LightList> lights;
        
        public Queue() {}
        public Queue(String name, GeometryComparator comparator, DepthRange depth, boolean perspective) {
            if (comparator == null) {
                comparator = Queue.NULL_COMPARATOR;
            }
            this.name = name;
            this.queue = new GeometryQueue(comparator);
            this.queue.setDepth(depth);
            this.queue.setPerspective(perspective);
        }

        @Override
        public void write(JmeExporter ex) throws IOException {
            OutputCapsule out = ex.getCapsule(this);
            out.write(name, "name", "Opaque");
            out.write(queue, "queue", new GeometryQueue());
        }
        @Override
        public void read(JmeImporter im) throws IOException {
            InputCapsule in = im.getCapsule(this);
            name = in.readString("name", "Opaque");
            queue = SavableObject.readSavable(in, "queue", GeometryQueue.class, new GeometryQueue());
        }
        
    }
    
}
