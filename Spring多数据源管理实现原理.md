##Spring多数据源管理实现原理
### 应用场景：
大部分单一架构项目连接一台数据库服务器，但随着业务的增加数据库数据量不断飙升，数据库达到性能瓶颈，大部分技术人员都会对数据库主从配置；既然读写分离那就需要连接两个不同的数据库，这时候Spring多数据源管理类**AbstractRoutingDataSource**就要派上用场了（排除使用数据库集群管理工具统一管理的应用场景）

### 源码分析：

![Paste_Image.png](http://upload-images.jianshu.io/upload_images/3057341-1c43f64e72aa3630.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
通过源码可以看出该类是一个抽象类，定义了6个属性。
######targetDataSources：是一个map类型该属性正是用来维护项目中多个数据源
######defaultTargetDataSource：通过属性名很直观的可以理解它的作用（默认数据源）
######lenientFallback：默认为true，无需改动
######dataSourceLookup：查找数据源接口的名称

![Paste_Image.png](http://upload-images.jianshu.io/upload_images/3057341-39f6a949a7a14a85.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
该类是一个interface并且只有一个方法getDataSource，通过方法的参数名称应该清楚传入一个字符类型的数据源名称获取DataSource
######resolvedDataSources：如果该字段没有赋值，就是targetDataSources
######resolvedDefaultDataSource：改变后的数据源
###深入理解：
使用数据源的目的就是要获取Connection，接下来就从AbstractRoutingDataSource的getConnection方法一探究竟。

![Paste_Image.png](http://upload-images.jianshu.io/upload_images/3057341-9c37f8de65f637e9.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
直接进入determineTargetDataSource方法

![Paste_Image.png](http://upload-images.jianshu.io/upload_images/3057341-6dcf3da7801b74cf.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
该方法的作用是检索目标数据源
第一句代码
Assert.notNull(this.resolvedDataSources, "DataSource router not initialized");

![Paste_Image.png](http://upload-images.jianshu.io/upload_images/3057341-a48cb1db8345fc69.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
判断resolvedDataSources是否为空，为空就抛出异常。

![Paste_Image.png](http://upload-images.jianshu.io/upload_images/3057341-04dc910aeb4d85ee.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
通过afterPropertiesSet方法看出如果resolvedDataSources为空就遍历targetDataSources并把对应的key，value put到resolvedDataSources
第二句代码
Object lookupKey = determineCurrentLookupKey();
进入determineCurrentLookupKey方法

![Paste_Image.png](http://upload-images.jianshu.io/upload_images/3057341-66e35fae85061677.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
该方法是一个抽象方法，返回要从resolvedDataSources查找key，该方法还会实现检查线程<font color=red>绑定事务</font>上下文。
第三局代码
DataSource dataSource = this.resolvedDataSources.get(lookupKey);
从resolvedDataSources中取出数据源并返回

###代码实现
项目地址：[https://github.com/yangzhaojava/common-project](https://github.com/yangzhaojava/common-project)
实现AbstractRoutingDataSource重写determineCurrentLookupKey

![Paste_Image.png](http://upload-images.jianshu.io/upload_images/3057341-f4258efd1c5a922c.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
定义DataSourceContextHolder

![Paste_Image.png](http://upload-images.jianshu.io/upload_images/3057341-48e3b2876b444488.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
通过ThreadLocal类使每个线程获取独立的数据源，防止并发访问时获取错误的数据源
#####基于SpringAop实现数据源动态切换
注解类DataSource

```
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
```


增强类（DataSouceAdvisor）
![image.png](http://upload-images.jianshu.io/upload_images/3057341-c560db0f4b80d568.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

数据源名称动态绑定类（DataSourceContextHolder）
![image.png](http://upload-images.jianshu.io/upload_images/3057341-2cad916e12766381.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

核心管理类（DataSourceManager真正实现切换）

```
/**
 * 数据源切换管理类
 * 
 * @author yangzhao
 * Created by  17/2/7.
 */
@Component
public class DataSourceManager implements BeanFactoryPostProcessor {

    private final Logger logger = LogManager.getLogger(DataSourceManager.class);
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
        return configs;
    }
}

```

项目地址：[https://github.com/yangzhaojava/common-project](https://github.com/yangzhaojava/common-project)  **multiple-data-sources**模块下

以上属于原创文章，转载请注明作者[@怪咖](https://juejin.im/user/57a0281fd342d300572864dd)
QQ:208275451


