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
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import java.io.IOException;

/**
 * Merges several inputs into one output by choosing one input to connect to
 * the output using a controllable index.
 * <p>
 * Junction can either function as a set of individual inputs (group size is 1), or as a set of
 * group inputs (group size is greater than 1).
 * <p>
 * Static methods should be used to correctly reference inputs and outputs.
 * 
 * @author codex
 * @param <T>
 */
public class Junction <T> extends RenderPass {
    
    private static final int EXTRA_INPUTS = 0;
    
    private int length;
    private int groupSize;
    private ResourceTicket<T> output;
    private GraphSource<Integer> source;
    private int defaultIndex = 0;
    private int curIndex = -1;
    
    public Junction() {
        this(2);
    }
    public Junction(int length) {
        setLength(length);
    }
    public Junction(int length, int groupSize) {
        setLength(length);
        setGroupSize(groupSize);
    }
    
    @Override
    protected void initialize(FrameGraph frameGraph) {
        if (groupSize == 1) {
            addInputGroup(getInput(), length);
        } else for (int i = 0; i < length; i++) {
            addInputGroup(getInput(i), groupSize);
        }
        if (groupSize > 1) {
            addOutputGroup(getOutput(), groupSize);
        } else {
            output = addOutput(getOutput());
        }
    }
    @Override
    protected void prepare(FGRenderContext context) {
        if (source != null) {
            connect(source.getGraphValue(frameGraph, context.getViewPort()));
        } else {
            connect(defaultIndex);
        }
    }
    @Override
    protected void execute(FGRenderContext context) {}
    @Override
    protected void reset(FGRenderContext context) {}
    @Override
    protected void cleanup(FrameGraph frameGraph) {}
    @Override
    public boolean isUsed() {
        // This pass will never execute
        return false;
    }
    @Override
    public void write(JmeExporter ex) throws IOException {
        super.write(ex);
        OutputCapsule out = ex.getCapsule(this);
        out.write(length, "length", 2);
        out.write(groupSize, "groupSize", 1);
        out.write(defaultIndex, "defaultIndex", 0);
        out.write(new SavableObject(source), "source", SavableObject.NULL);
    }
    @Override
    public void read(JmeImporter im) throws IOException {
        super.read(im);
        InputCapsule in = im.getCapsule(this);
        length = in.readInt("length", 2);
        groupSize = in.readInt("groupSize", 1);
        defaultIndex = in.readInt("defaultIndex", 0);
        source = SavableObject.read(in, "source", GraphSource.class);
    }
    
    private void connect(int i) {
        boolean assignNull = i < 0 || i >= length;
        if (i != curIndex) {
            frameGraph.setLayoutUpdateNeeded();
            curIndex = i;
        }
        if (groupSize > 1) {
            ResourceTicket[] outArray = getGroupArray(getOutput());
            if (!assignNull) {
                ResourceTicket[] inArray = getGroupArray(getInput(i));
                for (int j = 0; j < groupSize; j++) {
                    outArray[j].setSource(inArray[j]);
                }
            } else for (ResourceTicket t : outArray) {
                t.setSource(null);
            }
        } else {
            output.setSource(assignNull ? null : getGroupArray(getInput())[i]);
        }
    }
    
    public final void setLength(int length) {
        if (isAssigned()) {
            throw new IllegalStateException("Cannot change length while assigned.");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be greater than zero.");
        }
        this.length = length;
    }
    public final void setGroupSize(int groupSize) {
        if (isAssigned()) {
            throw new IllegalStateException("Cannot alter group size while assigned to a framegraph.");
        }
        if (groupSize <= 0) {
            throw new IllegalArgumentException("Group length must be greater than zero.");
        }
        this.groupSize = groupSize;
    }
    public void setIndexSource(GraphSource<Integer> source) {
        this.source = source;
    }
    public void setDefaultIndex(int defaultIndex) {
        this.defaultIndex = defaultIndex;
    }
    
    public int getLength() {
        return length;
    }
    public int getGroupSize() {
        return groupSize;
    }
    public GraphSource<Integer> getIndexSource() {
        return source;
    }
    public int getDefaultIndex() {
        return defaultIndex;
    }
    
    /**
     * 
     * @return 
     */
    public static String getInput() {
        return "Input";
    }
    /**
     * Returns a string referencing an individual input (groupSize = 1) or 
     * an input group (groupSize &gt; 1).
     * 
     * @param i index of input or input group
     * @return 
     */
    public static String getInput(int i) {
        return "Input["+i+"]";
    }
    /**
     * Returns a string referencing an individual input that is part of
     * a group (groupSize &gt; 1 only).
     * 
     * @param i index of group
     * @param j index of input in group
     * @return 
     */
    public static String getInput(int i, int j) {
        return "Input["+i+"]["+j+"]";
    }
    /**
     * Returns a string referencing the individual output (groupSize = 1)
     * or the output group (groupSize &gt; 1).
     * 
     * @return 
     */
    public static String getOutput() {
        return "Value";
    }
    /**
     * Returns a string referencing an individual output in the output group
     * (groupSize &gt; 1 only).
     * 
     * @param i index of output in group
     * @return 
     */
    public static String getOutput(int i) {
        return "Value["+i+"]";
    }
    
}
