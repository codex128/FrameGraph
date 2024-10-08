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
package codex.renthyl.modules;

import codex.boost.export.SavableObject;
import codex.renthyl.FGRenderContext;
import codex.renthyl.FrameGraph;
import codex.renthyl.resources.ResourceTicket;
import codex.renthyl.client.GraphSource;
import codex.renthyl.client.GraphTarget;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.NullSavable;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import java.io.IOException;
import java.util.LinkedList;

/**
 * Interface pass between the framegraph and game logic, allowing them to communicate.
 * <p>
 * Game logic can listen to framegraph parameters via {@link GraphTarget}s, and/or game logic
 * can communicate parameters to the framegraph via a {@link GraphSource}.
 * <p>
 * Objects handled by this pass are automatically marked as constant, so that future changes
 * do not taint the game logic's resource view.
 * <p>
 * Inputs:
 * <ul>
 *   <li>{@link #INPUT} ({@link Object}): the value to share with game logic via registered GraphTargets (optional).</li>
 * </ul>
 * Outputs:
 * <ul>
 *   <li>{@link #OUTPUT} ({@link Object}): the value to share with the FrameGraph from game logic using the registered GraphSource</li>
 * </ul>
 * 
 * @author codex
 * @param <T>
 */
public class Attribute <T> extends RenderPass {
    
    public static final String INPUT = "Input", OUTPUT = "Output";
    
    private ResourceTicket<T> in, out;
    private T defaultValue = null;
    private GraphSource<T> source;
    private final LinkedList<GraphTarget<T>> targets = new LinkedList<>();
    
    @Override
    protected void initialize(FrameGraph frameGraph) {
        this.
        in = addInput(INPUT);
        out = addOutput(OUTPUT);
    }
    @Override
    protected void prepare(FGRenderContext context) {
        declare(null, out);
        referenceOptional(in);
    }
    @Override
    protected void execute(FGRenderContext context) {
        T inVal = resources.acquireOrElse(in, null);
        if (inVal != null && !targets.isEmpty()) {
            boolean used = false;
            for (GraphTarget<T> t : targets) {
                if (t.setGraphValue(frameGraph, context.getViewPort(), inVal)) {
                    used = true;
                }
            }
            if (used) {
                resources.setConstant(in);
            }
        }
        T outVal;
        if (source != null) {
            outVal = source.getGraphValue(frameGraph, context.getViewPort());
        } else {
            outVal = defaultValue;
        }
        if (outVal != null) {
            resources.setPrimitive(out, outVal);
        } else {
            resources.setUndefined(out);
        }
    }
    @Override
    protected void reset(FGRenderContext context) {}
    @Override
    protected void cleanup(FrameGraph frameGraph) {
        targets.clear();
        source = null;
    }
    @Override
    public boolean isUsed() {
        return super.isUsed() || in.hasSource();
    }
    @Override
    public void write(JmeExporter ex) throws IOException {
        super.write(ex);
        OutputCapsule out = ex.getCapsule(this);
        SavableObject.writeFromCollection(out, targets, "targets");
        out.write(new SavableObject(source), "source", SavableObject.NULL);
        out.write(new SavableObject(defaultValue), "default", SavableObject.NULL);
    }
    @Override
    public void read(JmeImporter im) throws IOException {
        super.read(im);
        InputCapsule in = im.getCapsule(this);
        SavableObject.readToCollection(in, "targets", targets);
        source = (GraphSource<T>)in.readSavable("source", null);
        defaultValue = (T)in.readSavable("default", null);
    }
    
    /**
     * Adds the graph target.
     * <p>
     * If any targets are recieving from this Attribute, the incoming
     * object will be marked as constant.
     * 
     * @param target target to add (not null)
     */
    public void addTarget(GraphTarget<T> target) {
        targets.add(target);
    }
    /**
     * Removes the graph target.
     * 
     * @param target target to remove (not null)
     */
    public void removeTarget(GraphTarget<T> target) {
        targets.remove(target);
    }
    /**
     * Sets the graph source.
     * 
     * @param source 
     */
    public void setSource(GraphSource<T> source) {
        this.source = source;
    }
    /**
     * 
     * @param defaultValue 
     */
    public void setDefaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * 
     * @return 
     */
    public T getDefaultValue() {
        return defaultValue;
    }
    
    /**
     * 
     * @return 
     */
    public static String getInput() {
        return "Value";
    }
    /**
     * 
     * @param i
     * @return 
     */
    public static String getInput(int i) {
        return "Value["+i+"]";
    }
    /**
     * 
     * @return 
     */
    public static String getOutput() {
        return "Value";
    }
    /**
     * 
     * @param i
     * @return 
     */
    public static String getOutput(int i) {
        return "Value["+i+"]";
    }
    
}
