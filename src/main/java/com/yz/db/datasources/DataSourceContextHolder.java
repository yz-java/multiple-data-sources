package com.yz.db.datasources;

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
