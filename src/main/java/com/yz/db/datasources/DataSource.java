package com.yz.db.datasources;

import java.lang.annotation.*;

/**
 * 数据源
 * Created by yangzhao on 17/2/7.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataSource {
    String value() default "defaultSource";
}
