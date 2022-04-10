package com.github.jitwxs.easydata.sample.core.mock.mocker.implicit;

import com.github.jitwxs.easydata.sample.common.bean.MockConfig;
import com.github.jitwxs.easydata.sample.core.mock.mocker.IMocker;
import com.github.jitwxs.easydata.sample.provider.ExplicitMockerProvider;
import com.github.jitwxs.easydata.sample.provider.ProviderFactory;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

public class ClassMocker implements IMocker<Object> {
    private final Class<?> clazz;

    private final Type[] genericTypes;

    private final ExplicitMockerProvider explicitProvider = ProviderFactory.delegate(ExplicitMockerProvider.class);

    public ClassMocker(Class<?> clazz, Type[] genericTypes) {
        this.clazz = clazz;
        this.genericTypes = genericTypes;
    }

    @Override
    public Object mock(MockConfig mockConfig) {
        IMocker<?> mocker;
        if (clazz.isArray()) {
            mocker = new ArrayMocker(clazz);
        } else if (Map.class.isAssignableFrom(clazz)) {
            mocker = new MapMocker(genericTypes);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            mocker = new CollectionMocker(clazz, genericTypes[0]);
        } else if (clazz.isEnum()) {
            mocker = new EnumMocker<>(clazz);
        } else {
            mocker = explicitProvider.delegate(clazz);
            if (mocker == null) {
                mocker = new BeanMocker(clazz);
            }
        }
        return mocker.mock(mockConfig);
    }

}
