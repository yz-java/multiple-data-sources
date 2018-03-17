## Spring多数据源管理实现原理

[TOC]

### 应用场景：
大部分单一架构项目连接一台数据库服务器，但随着业务的增加数据库数据量不断飙升，数据库达到性能瓶颈，大部分技术人员都会对数据库主从配置；既然读写分离那就需要连接两个不同的数据库，这时候Spring多数据源管理类**AbstractRoutingDataSource**就要派上用场了（排除使用数据库集群管理工具统一管理的应用场景）

### 源码分析：

```
public abstract class AbstractRoutingDataSource extends AbstractDataSource implements InitializingBean {

	private Map<Object, Object> targetDataSources;

	private Object defaultTargetDataSource;

	private boolean lenientFallback = true;

	private DataSourceLookup dataSourceLookup = new JndiDataSourceLookup();

	private Map<Object, DataSource> resolvedDataSources;

	private DataSource resolvedDefaultDataSource;
	...
}
```

通过源码可以看出该类是一个抽象类，定义了6个属性。
**targetDataSources：是一个map类型该属性正是用来维护项目中多个数据源
defaultTargetDataSource：通过属性名很直观的可以理解它的作用（默认数据源）
lenientFallback：默认为true，无需改动
dataSourceLookup：查找数据源接口的名称
resolvedDataSources：如果该字段没有赋值，就是targetDataSources
resolvedDefaultDataSource：改变后的数据源**

```
public interface DataSourceLookup {

	/**
	 * Retrieve the DataSource identified by the given name.
	 * @param dataSourceName the name of the DataSource
	 * @return the DataSource (never {@code null})
	 * @throws DataSourceLookupFailureException if the lookup failed
	 */
	DataSource getDataSource(String dataSourceName) throws DataSourceLookupFailureException;

}
```

该类是一个interface并且只有一个方法getDataSource，通过方法的参数名称应该清楚传入一个字符类型的数据源名称获取DataSource

#### 深入理解：
使用数据源的目的就是要获取Connection，接下来就从AbstractRoutingDataSource的getConnection方法一探究竟。

```
@Override
	public Connection getConnection() throws SQLException {
		return determineTargetDataSource().getConnection();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return determineTargetDataSource().getConnection(username, password);
	}
```

直接进入determineTargetDataSource方法

```
/**
	 * Retrieve the current target DataSource. Determines the
	 * {@link #determineCurrentLookupKey() current lookup key}, performs
	 * a lookup in the {@link #setTargetDataSources targetDataSources} map,
	 * falls back to the specified
	 * {@link #setDefaultTargetDataSource default target DataSource} if necessary.
	 * @see #determineCurrentLookupKey()
	 */
	protected DataSource determineTargetDataSource() {
		Assert.notNull(this.resolvedDataSources, "DataSource router not initialized");
		//该方法是一个抽象方法，返回要从resolvedDataSources查找key，该方法还会实现检查线程绑定事务上下文。
		Object lookupKey = determineCurrentLookupKey();
		//从resolvedDataSources中取出数据源并返回
		DataSource dataSource = this.resolvedDataSources.get(lookupKey);
		if (dataSource == null && (this.lenientFallback || lookupKey == null)) {
			dataSource = this.resolvedDefaultDataSource;
		}
		if (dataSource == null) {
			throw new IllegalStateException("Cannot determine target DataSource for lookup key [" + lookupKey + "]");
		}
		return dataSource;
	}

```


###代码实现

#### 实现AbstractRoutingDataSource重写determineCurrentLookupKey

```
/**
 * @author yangzhao
 * Created by 17/2/7.
 */
public class DynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        String dataSourceName = DataSourceContextHolder.getDataSourceName();
        return dataSourceName;
    }
}
```

#### 定义DataSourceContextHolder

```
/**
 * 该类内部维护了{@link ThreadLocal}
 * @author yangzhao
 * Created by 17/2/7.
 */
public class DataSourceContextHolder {

    private static final ThreadLocal<String> contextHolder = new ThreadLocal<String>();
    /**
     * @Description: 设置数据源类型
     * @param dataSourceName  数据源名称
     * @return void
     * @throws
     */
    public static void setDataSourceName(String dataSourceName) {contextHolder.set(dataSourceName);}

    /**
     * @Description: 获取数据源名称
     * @param
     * @return String
     * @throws
     */
    public static String getDataSourceName() {
        return contextHolder.get();
    }

    /**
     * @Description: 清除数据源名称
     * @param
     * @return void
     * @throws
     */
    public static void clearDataSource() {
        contextHolder.remove();
    }
}

```

通过ThreadLocal类使每个线程获取独立的数据源，防止并发访问时获取错误的数据源
#### 基于SpringAop实现数据源动态切换
##### 注解类DataSource

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


##### 增强类（DataSouceAdvisor）

```
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

```

##### 核心管理类（DataSourceManager真正实现切换）

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

项目地址：[https://github.com/yz-java/multiple-data-sources](https://github.com/yz-java/multiple-data-sources)

以上属于原创文章，转载请注明作者[@怪咖](https://juejin.im/user/57a0281fd342d300572864dd)
QQ:208275451


