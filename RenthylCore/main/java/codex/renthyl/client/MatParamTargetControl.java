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
package codex.renthyl.client;

import codex.renthyl.FrameGraph;
import com.jme3.material.Material;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphIterator;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.shader.VarType;

/**
 * Applies values from a FrameGraph to a material parameter.
 * <p>
 * The target material is the first located material in the controlled spatial's
 * hierarchy, however, this is mainly intended to control geometries.
 * 
 * @author codex
 * @param <T>
 */
public class MatParamTargetControl <T> extends AbstractControl implements GraphTarget<T> {
    
    private final String name;
    private final VarType type;
    private ViewPort[] viewPorts;
    private Material material;
    private T value;
    
    public MatParamTargetControl(String name, VarType type) {
        this.name = name;
        this.type = type;
    }
    
    @Override
    protected void controlUpdate(float tpf) {}
    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {}
    @Override
    public void setSpatial(Spatial spat) {
        if (spatial == spat) {
            return;
        }
        super.setSpatial(spat);
        if (spatial != null) {
            for (Spatial s : new SceneGraphIterator(spatial)) {
                if (s instanceof Geometry) {
                    material = ((Geometry)s).getMaterial();
                    break;
                }
            }
        } else {
            material = null;
        }
    }
    @Override
    public boolean setGraphValue(FrameGraph frameGraph, ViewPort viewPort, T value) {
        if (containsViewPort(viewPort)) {
            this.value = value;
            if (this.value != null) {
                material.setParam(name, type, this.value);
            } else {
                material.clearParam(name);
            }
            return true;
        }
        return false;
    }
    
    private boolean containsViewPort(ViewPort vp) {
        if (viewPorts == null) return true;
        for (ViewPort p : viewPorts) {
            if (p == vp) return true;
        }
        return false;
    }
    
    /**
     * Registers the ViewPorts that are able to affect the internal value.
     * <p>
     * ViewPorts not included in the array cannot affect the internal value.
     * 
     * @param viewPorts 
     */
    public void setViewPorts(ViewPort... viewPorts) {
        this.viewPorts = viewPorts;
    }
    /**
     * Sets the ViewPort filter to allow ViewPorts to affect the internal value.
     */
    public void includeAllViewPorts() {
        viewPorts = null;
    }
    
    /**
     * Gets the internal value.
     * 
     * @return 
     */
    public T getValue() {
        return value;
    }
    /**
     * Gets the array of ViewPorts that are able to affect the internal value.
     * 
     * @return array of ViewPorts, or null if all ViewPorts are accepted
     */
    public ViewPort[] getViewPorts() {
        return viewPorts;
    }
    
}
