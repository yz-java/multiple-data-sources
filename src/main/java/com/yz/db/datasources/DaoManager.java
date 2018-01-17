package com.yz.db.datasources;

import java.util.List;

/**
 * @author yangzhao
 *         create by 17/11/25
 */
public interface DaoManager<T> {
    /**
     * 根据传入对象不为空字段查询
     * @param t
     * @return
     */
    public List<T> select(T t);

    /**
     * 添加
     * @param t
     * @return
     */
    public boolean insert(T t);

    /**
     * 批量添加
     * @param ts
     * @return
     */
    public boolean batchInsert(List<T> ts);

    /**
     * 根据主键修改
     * @param t
     * @return
     */
    public boolean updateByPrimaryKey(T t);

    /**
     * 根据主键删除
     * @param t
     * @return
     */
    public boolean deleteByPrimaryKey(T t);

}
