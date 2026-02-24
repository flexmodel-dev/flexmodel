package dev.flexmodel.reflect;

import dev.flexmodel.model.EntityDefinition;

/**
 * @author cjbi
 */
public interface ProxyInterface {

  EntityDefinition entityInfo();

  Class<?> originClass();

}
