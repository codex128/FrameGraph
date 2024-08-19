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
package codex.renthyl.export;

import codex.boost.export.SavableObject;
import codex.renthyl.FrameGraph;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Holds savable data for a FrameGraph.
 * 
 * @author codex
 */
public class FrameGraphData implements Savable {
    
    private String name;
    private boolean dynamic;
    private ModuleGraphData modules;
    private ArrayList<SavableObject> settings = new ArrayList<>();
    
    public FrameGraphData() {}
    public FrameGraphData(FrameGraph frameGraph) {
        this.name = frameGraph.getName();
        this.dynamic = frameGraph.isDynamic();
        this.modules = frameGraph.createModuleData();
        HashMap<String, Object> settingsMap = frameGraph.getSettingsMap();
        for (String key : settingsMap.keySet()) {
            settings.add(new SavableObject(key, settingsMap.get(key)));
        }
    }
    
    @Override
    public void write(JmeExporter ex) throws IOException {
        OutputCapsule out = ex.getCapsule(this);
        out.write(name, "name", "FrameGraph");
        out.write(dynamic, "dynamic", false);
        out.write(modules, "modules", null);
        out.writeSavableArrayList(settings, "settings", new ArrayList<>());
    }
    @Override
    public void read(JmeImporter im) throws IOException {
        InputCapsule in = im.getCapsule(this);
        name = in.readString("name", "FrameGraph");
        dynamic = in.readBoolean("dynamic", false);
        modules = SavableObject.readSavable(in, "modules", ModuleGraphData.class, null);
        settings = in.readSavableArrayList("settings", new ArrayList<>());
    }

    public String getName() {
        return name;
    }
    public ModuleGraphData getModules() {
        return modules;
    }
    public ArrayList<SavableObject> getSettings() {
        return settings;
    }
    
}
