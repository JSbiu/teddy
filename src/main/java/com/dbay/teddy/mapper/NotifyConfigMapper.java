package com.dbay.teddy.mapper;

import com.dbay.teddy.entity.NotifyConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 通知配置的数据访问。表结构与 JobMapper 保持一致的命名和类型风格。
 *
 * @author AlexanderGuo
 */
@Mapper
public interface NotifyConfigMapper {

    /**
     * 建表。由 DataBaseInit 在探测到表不存在时调用。
     */
    @Update("create table notify_config(" +
            "id                 BIGINT PRIMARY KEY NOT NULL AUTO_INCREMENT," +
            "name               VARCHAR(100)," +
            "webhook            VARCHAR(500)," +
            "isDefault          SMALLINT," +
            "KEY (isDefault)" +
            ")" +
            "ENGINE=InnoDB DEFAULT CHARSET=utf8")
    void create();

    /**
     * 表不存在时抛异常，调用方据此判断是否需要建表。
     */
    @Select("select count(1) from notify_config")
    Integer count() throws Exception;

    /**
     * 默认项排在最前，方便页面直接展示。
     */
    @Select("select * from notify_config order by isDefault desc, id")
    List<NotifyConfig> list();

    @Select("select * from notify_config where isDefault = 1 limit 1")
    NotifyConfig findDefault();

    @Select("select * from notify_config where id = #{id}")
    NotifyConfig findOne(@Param("id") Integer id);

    @Insert("insert into notify_config(name, webhook, isDefault) " +
            "values(#{t.name}, #{t.webhook}, #{t.isDefault})")
    void save(@Param("t") NotifyConfig config);

    @Insert("update notify_config set " +
            "name = #{t.name}," +
            "webhook = #{t.webhook}," +
            "isDefault = #{t.isDefault}" +
            " where id = #{t.id}")
    void update(@Param("t") NotifyConfig config);

    /**
     * 清空默认标记，配合 save 保证全局最多一条默认项。
     */
    @Update("update notify_config set isDefault = 0 where isDefault = 1")
    void clearDefault();

    @Delete("delete from notify_config where id = #{id}")
    void delete(@Param("id") Integer id);
}
