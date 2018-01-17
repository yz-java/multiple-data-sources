package com.yz.db.datasources;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 增强类
 * 实现MethodInterceptor接口，通过反射动态解析方法是否标注@DataSource {@link DataSource}注解。
 * 如果已标注@DataSource注解取值，set到{@link DataSourceContextHolder}
 * @author yangzhao
 *         create by 17/10/20
 */
@Component("dataSourceAdvisor")
public class DataSouceAdvisor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        Method method = methodInvocation.getMethod();
        Object aThis = methodInvocation.getThis();
        //设置默认数据库
        DataSourceContextHolder.setDataSourceName("defaultSource");

        DataSource dataSource = aThis.getClass().getAnnotation(DataSource.class);
        if (dataSource!=null){
            DataSourceContextHolder.setDataSourceName(dataSource.value());
        }
        dataSource = method.getAnnotation(DataSource.class);
        if (dataSource!=null){
            DataSourceContextHolder.setDataSourceName(dataSource.value());
        }
        Object proceed = null;
        try {
            proceed = methodInvocation.proceed();
        }catch (Exception e){
            throw e;
        }finally {
            DataSourceContextHolder.clearDataSource();
        }
        return proceed;
    }
}
