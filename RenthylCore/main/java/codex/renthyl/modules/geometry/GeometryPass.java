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
import codex.renthyl.FGRenderContext;
import codex.renthyl.FrameGraph;
import codex.renthyl.GeometryQueue;
import codex.renthyl.resources.ResourceTicket;
import codex.renthyl.definitions.TextureDef;
import codex.renthyl.modules.RenderPass;
import codex.boost.render.DepthRange;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import java.io.IOException;

/**
 * Renders a queue bucket to a set of color and depth textures.
 * <p>
 * Inputs:
 * <ul>
 *   <li>Geometry ({@link GeometryQueue}): queue of geometries to render.</li>
 *   <li>Color ({@link Texture2D}): Color texture to combine (by depth comparison) with the result of this render (optional).</li>
 *   <li>Depth ({@link Texture2D}): Depth texture to combine (by depth comparison) with the result of this render (optional).</li>
 * </ul>
 * Outputs:
 * <ul>
 *   <li>Color ({@link Texture2D}): Resulting color texture.</li>
 *   <li>Depth ({@link Texture2D}): Resulting depth texture.</li>
 * </ul>
 * 
 * @author codex
 */
public class GeometryPass extends RenderPass {
    
    private final DepthRange depth = new DepthRange();
    private ResourceTicket<Texture2D> inColor, inDepth, outColor, outDepth;
    private ResourceTicket<GeometryQueue> geometry;
    private TextureDef<Texture2D> colorDef, depthDef;
    private boolean perspective;
    
    public GeometryPass() {
        this(DepthRange.NORMAL, true);
    }
    public GeometryPass(DepthRange depth) {
        this(depth, true);
    }
    public GeometryPass(DepthRange depth, boolean perspective) {
        this.depth.set(depth);
        this.perspective = perspective;
    }
    
    @Override
    protected void initialize(FrameGraph frameGraph) {
        inColor = addInput("Color");
        inDepth = addInput("Depth");
        geometry = addInput("Geometry");
        outColor = addOutput("Color");
        outDepth = addOutput("Depth");
        colorDef = new TextureDef<>(Texture2D.class, img -> new Texture2D(img));
        depthDef = new TextureDef<>(Texture2D.class, img -> new Texture2D(img), Image.Format.Depth);
        colorDef.setFormatFlexible(true);
        depthDef.setFormatFlexible(true);
    }
    @Override
    protected void prepare(FGRenderContext context) {
        int w = context.getWidth();
        int h = context.getHeight();
        colorDef.setSize(w, h);
        depthDef.setSize(w, h);
        declare(colorDef, outColor);
        declare(depthDef, outDepth);
        reserve(outColor, outDepth);
        reference(geometry);
        referenceOptional(inColor, inDepth);
    }
    @Override
    protected void execute(FGRenderContext context) {
        FrameBuffer fb = getFrameBuffer(context, 1);
        resources.acquireColorTarget(fb, outColor);
        resources.acquireDepthTarget(fb, outDepth);
        context.getRenderer().setFrameBuffer(fb);
        context.getRenderer().clearBuffers(true, true, true);
        context.getRenderer().setBackgroundColor(ColorRGBA.BlackNoAlpha);
        context.renderTextures(resources.acquireOrElse(inColor, null), resources.acquireOrElse(inDepth, null));
        //context.getRenderer().setDepthRange(depth);
        //if (!perspective) {
        //    context.getRenderManager().setCamera(context.getViewPort().getCamera(), true);
        //}
        context.renderGeometry(resources.acquire(geometry), null, null);
        //if (!perspective) {
        //    context.getRenderManager().setCamera(context.getViewPort().getCamera(), false);
        //}
    }
    @Override
    protected void reset(FGRenderContext context) {}
    @Override
    protected void cleanup(FrameGraph frameGraph) {}
    @Override
    public void write(JmeExporter ex) throws IOException {
        super.write(ex);
        OutputCapsule out = ex.getCapsule(this);
        out.write(depth, "depth", DepthRange.NORMAL);
        out.write(perspective, "perspective", true);
    }
    @Override
    public void read(JmeImporter im) throws IOException {
        super.read(im);
        InputCapsule in = im.getCapsule(this);
        depth.set(SavableObject.readSavable(in, "depth", DepthRange.class, DepthRange.NORMAL));
        perspective = in.readBoolean("perspective", true);
    }
    
    /**
     * Sets the depth range objects are rendered within.
     * 
     * @param depth depth range (not null, unaffected)
     */
    public void setDepthRange(DepthRange depth) {
        this.depth.set(depth);
    }
    
    /**
     * Gets the depth range objects are rendered within.
     * 
     * @return 
     */
    public DepthRange getDepthRange() {
        return depth;
    }
    
    /**
     * 
     * @param perspective 
     */
    public void setPerspective(boolean perspective) {
        this.perspective = perspective;
    }
    
    /**
     * 
     * @return 
     */
    public boolean isPerspective() {
        return perspective;
    }
    
}
