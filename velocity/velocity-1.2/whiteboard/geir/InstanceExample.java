/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Velocity", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import java.io.StringWriter;
import java.util.Properties;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.VelocityContext;

import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.MethodInvocationException;

/**
 * This class is a simple demonstration of how to use multiple instances
 * of the Velocity engine.
 *
 * @author <a href="mailto:geirm@optonline.net">Geir Magnusson Jr.</a>
 * @version $Id: InstanceExample.java,v 1.1 2001/07/16 04:45:48 geirm Exp $
 */

public class InstanceExample
{
    public static void main( String args[] )
    {
        /* first, we init the runtime engine.  Defaults are fine. */
        
        VelocityEngine ve1 = new VelocityEngine();
        VelocityEngine ve2 = new VelocityEngine();

        try
        {
            ve1.setProperty("runtime.log", "velengine1.log");
            ve1.setProperty("file.resource.loader.path", "./template1");
            ve1.init();
            
            ve2.setProperty("runtime.log", "velengine2.log");
            ve2.setProperty("file.resource.loader.path", "./template2");
            ve2.init();
        }
        catch(Exception e)
        {
            System.out.println("Problem initializing Velocity : " + e );
            return;
        }

        /* lets make a Context and put data into it */

        VelocityContext context = new VelocityContext();

        context.put("name", "Velocity");
        context.put("project", "Jakarta");
        
        /* lets render a template */

        StringWriter w1 = new StringWriter();
        StringWriter w2 = new StringWriter();

        try
        {
            ve1.mergeTemplate("instanceexample.vm", context, w1);
            ve2.mergeTemplate("instanceexample.vm", context, w2);
        }
        catch (Exception e )
        {
            System.out.println("Problem merging template : " + e );
        }

        System.out.println(" template 1 : " + w1 );
        System.out.println(" template 2 : " + w2 );
    }
}
