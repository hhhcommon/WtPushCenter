package com.woting.push.core.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.annotation.Resource;
import javax.sql.DataSource;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
//import com.spiritdata.framework.core.cache.CacheEle;
//import com.spiritdata.framework.core.cache.SystemCache;

/**
 * 加载系统缓存内容
 * <pre>
 * 注意系统缓存不同于redis的缓存。
 * 系统缓存为全局作用域下的，用于本系统管理控制的缓存
 * </pre>
 * @author wanghui
 */
@Service
public class LoadSysCacheService {
//    private Logger logger=LoggerFactory.getLogger(LoadSysCacheService.class);
    @Resource
    private DataSource dataSource;
    /**
     * 加载内容管理中的资源库数据，为数据导入做准备
     */
    public void loadCache() {
//        addTestUser();
//        //加载栏目结构
//        try {
//            _CacheChannel _cc=channelService.loadCache();
//            SystemCache.setCache(new CacheEle<_CacheChannel>(CrawlerConstants.CM_CHANNEL, "栏目结构", _cc));
//            logger.info("加载资源库[栏目结构]成功");
//        } catch(Exception e) {
//            logger.info("加载资源库[栏目结构]失败，{}", e.getMessage());
//        }
    }

//    private void addTestUser() {
//        Connection conn=null;
//        PreparedStatement ps=null;
//        try {
//            conn=dataSource.getConnection();
//            conn.setAutoCommit(false);
//            long a=System.currentTimeMillis();
//            ps=conn.prepareStatement("insert into plat_User values(?, ?, ?, ?, null, null, ?, null, null, null, null, 1, 0, 1, null, null, null, null, current_timestamp(), current_timestamp())");
//            int i=13001;
//            for (; i<=100000; i++) {
//                ps.setString(1, "TEST"+(1000000+i));
//                ps.setString(2, "TEST"+(1000000+i));
//                ps.setString(3, "TEST"+(1000000+i));
//                ps.setString(4, "TEST"+(1000000+i));
//                ps.setString(5, "aaaaaa");
//                ps.execute();
//                if (i%500==0) {
//                    conn.commit();
//                    System.out.println("insert "+i+" Time："+(System.currentTimeMillis()-a));
//                }
//            }
//            ps.execute();
//            conn.commit();
//            System.out.println("insert All "+i+" Time："+(System.currentTimeMillis()-a));
//        } catch (SQLException e) {
//            e.printStackTrace();
//        } finally {
//            if (ps!=null) try {ps.close();ps=null;} catch(Exception e) {ps=null;} finally {ps=null;};
//            if (conn!=null) try {conn.close();conn=null;} catch(Exception e) {conn=null;} finally {conn=null;};
//        }
//    }
}