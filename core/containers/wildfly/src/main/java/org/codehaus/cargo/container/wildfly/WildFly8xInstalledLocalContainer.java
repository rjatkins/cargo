/*
 * ========================================================================
 *
 * Codehaus CARGO, copyright 2004-2011 Vincent Massol.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ========================================================================
 */
package org.codehaus.cargo.container.wildfly;

import java.io.File;

import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.jboss.JBoss72xInstalledLocalContainer;
import org.codehaus.cargo.container.jboss.JBossPropertySet;
import org.codehaus.cargo.container.property.GeneralPropertySet;
import org.codehaus.cargo.container.spi.jvm.JvmLauncher;

/**
 * WildFly 8.x series container implementation.
 * 
 * @version $Id$
 */
public class WildFly8xInstalledLocalContainer extends JBoss72xInstalledLocalContainer
{
    /**
     * WildFly 8.x series unique id.
     */
    public static final String ID = "wildfly8x";

    /**
     * {@inheritDoc}
     * @see JBoss72xInstalledLocalContainer#JBoss72xInstalledLocalContainer(LocalConfiguration)
     */
    public WildFly8xInstalledLocalContainer(LocalConfiguration configuration)
    {
        super(configuration);
    }

    /**
     * {@inheritDoc}
     * @see org.codehaus.cargo.container.Container#getId()
     */
    @Override
    public String getId()
    {
        return ID;
    }

    /**
     * {@inheritDoc}
     * @see org.codehaus.cargo.container.Container#getName()
     */
    @Override
    public String getName()
    {
        return "WildFly " + getVersion("8.x");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStop(JvmLauncher java) throws Exception
    {
        String host =
            getConfiguration().getPropertyValue(GeneralPropertySet.HOSTNAME);
        String port =
            getConfiguration().getPropertyValue(JBossPropertySet.JBOSS_MANAGEMENT_HTTP_PORT);

        java.setJarFile(new File(getHome(), "jboss-modules.jar"));

        String modules = getConfiguration().getPropertyValue(
            JBossPropertySet.ALTERNATIVE_MODULES_DIR);
        if (!new File(modules).isAbsolute())
        {
            modules = getFileHandler().append(getHome(), modules);
        }

        java.addAppArguments(
            "-mp", modules,
            "org.jboss.as.cli",
            "--connect", "--controller=" + host + ":" + port,
            "command=:shutdown");

        java.start();
    }
}
