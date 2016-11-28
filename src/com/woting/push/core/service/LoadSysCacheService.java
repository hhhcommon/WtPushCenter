package com.woting.push.core.service;

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

    /**
     * 加载内容管理中的资源库数据，为数据导入做准备
     */
    public void loadCache() {
//        //加载栏目结构
//        try {
//            _CacheChannel _cc=channelService.loadCache();
//            SystemCache.setCache(new CacheEle<_CacheChannel>(CrawlerConstants.CM_CHANNEL, "栏目结构", _cc));
//            logger.info("加载资源库[栏目结构]成功");
//        } catch(Exception e) {
//            logger.info("加载资源库[栏目结构]失败，{}", e.getMessage());
//        }
    }
}