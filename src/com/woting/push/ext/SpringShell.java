package com.woting.push.ext;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
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
        String contextConfFilePath=((CacheEle<String>)SystemCache.getCache(PushConstants.APP_PATH)).getContent();
        String os = System.getProperty("os.name");
        if (os.toLowerCase().startsWith("linux")||os.toLowerCase().startsWith("unix")||os.toLowerCase().startsWith("aix")) contextConfFilePath="conf/appContext.xml";
        else if (os.toLowerCase().startsWith("window")) contextConfFilePath+="conf/appContext.xml";

        SystemCache.setCache(new CacheEle<ApplicationContext>(PushConstants.CONTEXT_SPRINGCTX, "Spring ICO上下文",
                new FileSystemXmlApplicationContext(new String[] {contextConfFilePath})));
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
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            LoggerFactory.getLogger(SpringShell.class).error("得到名称为[{}]的bean对象出现异常：\n{}", beanName, sw.toString());
        }
        return null;
    }
}