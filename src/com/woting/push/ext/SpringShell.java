package com.woting.push.ext;

import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.util.StringUtils;
import com.woting.push.PushConstants;

/**
 * 为Spring所做的壳程序
 * @author wanghui
 */
public abstract class SpringShell {
    /**
     * Spring ICO上下文加载
     */
    public static void init() {
        //得到系统路径
        @SuppressWarnings("unchecked")
        String contextConfFilePath=((CacheEle<String>)SystemCache.getCache(PushConstants.APP_PATH)).getContent();
        String os=System.getProperty("os.name");
        if (os.toLowerCase().startsWith("linux")||os.toLowerCase().startsWith("unix")||os.toLowerCase().startsWith("aix")) contextConfFilePath="conf/appContext.xml";
        else if (os.toLowerCase().startsWith("window")) contextConfFilePath+="conf/appContext.xml";

        SystemCache.setCache(new CacheEle<ApplicationContext>(PushConstants.CONTEXT_SPRINGCTX, "Spring ICO上下文",
                new FileSystemXmlApplicationContext(new String[] {contextConfFilePath}))
        );
    }

    /**
     * 得到某个Bean
     * @param beanName bean名称
     * @return bean对象
     */
    public static Object getBean(String beanName) {
        try {
            return ((ApplicationContext)(SystemCache.getCache(PushConstants.CONTEXT_SPRINGCTX).getContent())).getBean(beanName);
        } catch(Exception e) {
            LoggerFactory.getLogger(SpringShell.class).error("得到名称为[{}]的bean对象出现异常：\n{}", beanName, StringUtils.getAllMessage(e));
        }
        return null;
    }
}