package com.yz.db.datasources;

import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据源切换管理类
 *
 * @author yangzhao
 * Created by  17/2/7.
 */
@Component
public class DataSourceManager implements BeanFactoryPostProcessor {

    private final Logger logger = LoggerFactory.getLogger(DataSourceManager.class);
    /**
     * 扫描包
     * 一般项目都是以com开头所以这里默认为com
     */
    private String pacakgePath = "com";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        //getconfigs
        List<String> configs = getconfigs().stream().collect(Collectors.toList());

        //打印所有生成的expression配置信息
        configs.forEach(s -> logger.info(s));

        //设置aop信息
        setAopInfo(configs,beanFactory);
    }

    /**
     * 设置注册bean动态AOP信息
     * @param configs
     * @param beanFactory
     */
    private void setAopInfo(List<String> configs, ConfigurableListableBeanFactory beanFactory) {

        if (beanFactory instanceof BeanDefinitionRegistry){
            BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) beanFactory;
            for (String config :configs) {
                //增强器
                RootBeanDefinition advisor = new RootBeanDefinition(DefaultBeanFactoryPointcutAdvisor.class);
                advisor.getPropertyValues().addPropertyValue("adviceBeanName",new RuntimeBeanReference("dataSourceAdvisor").getBeanName());
                //切点类
                RootBeanDefinition pointCut = new RootBeanDefinition(AspectJExpressionPointcut.class);
                pointCut.setScope(BeanDefinition.SCOPE_PROTOTYPE);
                pointCut.setSynthetic(true);
                pointCut.getPropertyValues().addPropertyValue("expression",config);

                advisor.getPropertyValues().addPropertyValue("pointcut",pointCut);
                //注册到spring容器
                String beanName = BeanDefinitionReaderUtils.generateBeanName(advisor, beanDefinitionRegistry,false);
                beanDefinitionRegistry.registerBeanDefinition(beanName,advisor);
            }
        }

    }
    public Set<String> getconfigs() {
        Set<String> configs = new HashSet<>();
        Reflections reflections = new Reflections(new ConfigurationBuilder().addUrls(ClasspathHelper.forPackage(pacakgePath)));
        //获取所有标记@DataSource的类
        Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(DataSource.class);
        Iterator<Class<?>> iterator = typesAnnotatedWith.iterator();
        while (iterator.hasNext()){
            Class<?> next = iterator.next();
            //获取该类所有方法
            Method[] declaredMethods = next.getDeclaredMethods();
            for (Method method:declaredMethods){
                String classAndMethod = method.getDeclaringClass().getCanonicalName()+"."+method.getName();
                //生成expression配置
                String expression = "execution (* "+classAndMethod+"(..))";
                configs.add(expression);
            }
        }
        reflections = new Reflections(new ConfigurationBuilder().setUrls(ClasspathHelper.forPackage(pacakgePath)).setScanners(new MethodAnnotationsScanner()));
        //获取所有类中标记@DataSource的方法
        Set<Method> methodsAnnotatedWith = reflections.getMethodsAnnotatedWith(DataSource.class);
        Iterator<Method> it = methodsAnnotatedWith.iterator();
        while (it.hasNext()){
            Method method = it.next();
            String classAndMethod = method.getDeclaringClass().getCanonicalName()+"."+method.getName();
            //生成expression配置
            String expression = "execution (* "+classAndMethod+"(..))";
            configs.add(expression);
        }
        //手动配置BaseDao基类切点（解决spring aop 代理对父类不拦截问题）
        configs.add("execution (* com.yz.db.datasources.BaseDao.*(..))");
        return configs;
    }
}
