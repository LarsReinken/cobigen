package com.capgemini.cobigen.jsonplugin;

import java.util.List;

import com.capgemini.cobigen.api.extension.GeneratorPluginActivator;
import com.capgemini.cobigen.api.extension.Merger;
import com.capgemini.cobigen.api.extension.TriggerInterpreter;
import com.capgemini.cobigen.jsonplugin.merger.JSONMerger;
import com.google.common.collect.Lists;

/**
 * Activator for JSON plugin
 */
public class JSONPluginActivator implements GeneratorPluginActivator {

    @Override
    public List<Merger> bindMerger() {
        List<Merger> merger = Lists.newLinkedList();
        merger.add(new JSONMerger("sencharchmerge", false));
        merger.add(new JSONMerger("sencharchmerge_override", true));
        return merger;
    }

    @Override
    public List<TriggerInterpreter> bindTriggerInterpreter() {
        return null;
    }

}
