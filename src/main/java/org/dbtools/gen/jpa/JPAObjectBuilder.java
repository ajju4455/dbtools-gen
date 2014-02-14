package org.dbtools.gen.jpa;

import org.dbtools.gen.DBObjectBuilder;
import org.dbtools.gen.DBTableObjectBuilder;
import org.dbtools.gen.DBViewObjectBuilder;

/**
 * User: jcampbell
 * Date: 2/12/14
 */
public class JPAObjectBuilder extends DBObjectBuilder {
    @Override
    public DBTableObjectBuilder getTableObjectBuilder() {
        return new JPADBTableObjectBuilder();
    }

    @Override
    public DBViewObjectBuilder getViewObjectBuilder() {
        return null;
    }

    public static void buildAll(String schemaFilename, String baseOutputDir, String basePackageName, boolean injectionSupport, boolean dateTimeSupport) {
        DBObjectBuilder builder = new JPAObjectBuilder();
        builder.setXmlFilename(schemaFilename);
        builder.setOutputBaseDir(baseOutputDir);
        builder.setPackageBase(basePackageName);
        builder.setInjectionSupport(injectionSupport);
        builder.setDateTimeSupport(dateTimeSupport);

        builder.build();
        System.out.println("Generated [" + builder.getTableObjectBuilder().getNumberFilesGenerated() + "] files.");
    }

}