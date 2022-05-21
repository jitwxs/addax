package io.github.jitwxs.easydata.common.bean;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import io.github.jitwxs.easydata.common.exception.EasyDataException;
import io.github.jitwxs.easydata.common.function.ThrowableBiFunction;
import io.github.jitwxs.easydata.common.function.ThrowableFunction;
import io.github.jitwxs.easydata.common.util.ObjectUtils;
import io.github.jitwxs.easydata.common.util.ReflectionUtils;
import lombok.Builder;
import lombok.Data;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jitwxs@foxmail.com
 * @since 2022-05-19 23:32
 */
@Data
@Builder
public class FieldProperty {
    /**
     * 字段名
     */
    private String name;

    private Type type;

    private Class<?> target;

    private Field field;

    /**
     * 字段对应 get 方法
     */
    private ThrowableFunction<Object, ?> readFunc;

    /**
     * 字段对应 set 方法
     */
    private ThrowableBiFunction<Object, Object, ?> writeFunc;

    public boolean isReadable() {
        return this.readFunc != null;
    }

    public boolean isWriteable() {
        return this.writeFunc != null;
    }

    public static Map<String, FieldProperty> newInstance(final Class<?> target) throws IntrospectionException {
        final BeanInfo beanInfo = target.isInterface() ? Introspector.getBeanInfo(target) : Introspector.getBeanInfo(target, Object.class);

        final Map<String, PropertyDescriptor> descriptorMap = Arrays.stream(beanInfo.getPropertyDescriptors())
                .collect(Collectors.toMap(e -> deCapitalize(e.getName()), Function.identity()));

        if (Message.class.isAssignableFrom(target)) {
            return creatByProtoBean(target, descriptorMap);
        } else {
            return creatByJavaBean(target, descriptorMap);
        }
    }

    /**
     * 对于 Java 原生类构建
     * <p>
     * 使用 {@link Class#getDeclaredFields()} 为数据源，搭配 {@link BeanInfo#getPropertyDescriptors()} 获取字段
     *
     * @param target        class 对象
     * @param descriptorMap 基于自省机制的属性描述
     * @return (field_name, field_property)
     */
    private static Map<String, FieldProperty> creatByJavaBean(final Class<?> target, final Map<String, PropertyDescriptor> descriptorMap) {
        final Map<String, FieldProperty> resultMap = new HashMap<>();

        for (Field field : ReflectionUtils.getFieldsUpTo(target, Object.class)) {
            final String fieldName = field.getName();

            final PropertyDescriptor descriptor = descriptorMap.get(fieldName);


            resultMap.put(fieldName, FieldProperty.builder()
                    .name(fieldName)
                    .type(ReflectionUtils.getFieldType(field, descriptor))
                    .target(field.getType())
                    .field(field)
                    .readFunc(ReflectionUtils.getReadFunc(fieldName, descriptor, field))
                    .writeFunc(ReflectionUtils.getWriteFunc(fieldName, descriptor, field))
                    .build());
        }

        return resultMap;
    }

    /**
     * 对于 Protobuf 相关类构建
     * <p>
     * 使用 {@link BeanInfo#getPropertyDescriptors()} 为数据源，搭配 {@link Class#getDeclaredFields()} 获取字段
     *
     * @param target        class 对象
     * @param descriptorMap 基于自省机制的属性描述
     * @return (field_name, field_property)
     */
    private static Map<String, FieldProperty> creatByProtoBean(final Class<?> target, final Map<String, PropertyDescriptor> descriptorMap) {
        final MessageOrBuilder builder = (MessageOrBuilder) ObjectUtils.createProtoBuilder(target);
        if (builder == null) {
            throw new EasyDataException("FieldProperty create proto builder failed for " + target);
        }

        final Map<String, Field> fieldMap = Arrays.stream(target.getDeclaredFields())
                .collect(Collectors.toMap(e -> processProtoFieldName(e.getName()), Function.identity()));

        final Map<String, FieldProperty> resultMap = new HashMap<>();

        for (Descriptors.FieldDescriptor fieldDescriptor : builder.getDescriptorForType().getFields()) {
            final String fieldName = fieldDescriptor.getName();

            final PropertyDescriptor descriptor = descriptorMap.get(fieldName);
            if (descriptor == null) {
                continue;
            }

            final Field field = fieldMap.get(fieldName);

            resultMap.put(fieldName, FieldProperty.builder()
                    .name(fieldName)
                    .type(ReflectionUtils.getFieldType(field, descriptor))
                    .target(descriptor.getPropertyType())
                    .field(field)
                    .readFunc(ReflectionUtils.getReadFunc(fieldName, descriptor, null))
                    .writeFunc(ReflectionUtils.getWriteFunc(fieldName, descriptor, null))
                    .build());
        }

        return resultMap;
    }

    /**
     * java {@link Introspector} 机制会将 aBc 格式的字段，读取成 ABc，该方法将处理后的字段转回真实字段
     *
     * @param name 处理后的字段名
     * @return 真实的字段名
     */
    private static String deCapitalize(final String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
            char[] chars = name.toCharArray();
            chars[0] = Character.toLowerCase(chars[0]);
            return new String(chars);


        }
        return name;
    }

    /**
     * 处理 proto 字段名称，将名称最后的下划线去除
     *
     * @param name 未处理的 proto 字段名
     * @return 处理后的 proto 字段名
     */
    private static String processProtoFieldName(final String name) {
        if (name.endsWith("_")) {
            return name.substring(0, name.length() - 1);
        } else {
            return name;
        }
    }
}
