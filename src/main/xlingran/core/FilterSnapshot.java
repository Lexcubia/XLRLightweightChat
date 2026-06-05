package xlingran.core;

import xlingran.HopperTemplate;

/**
 * 登记时缓存的模板引用；tick 内不 resolve PDC。
 */
public final class FilterSnapshot {

    private final HopperTemplate template;

    public FilterSnapshot(HopperTemplate template) {
        this.template = template;
    }

    public HopperTemplate template() {
        return template;
    }
}
