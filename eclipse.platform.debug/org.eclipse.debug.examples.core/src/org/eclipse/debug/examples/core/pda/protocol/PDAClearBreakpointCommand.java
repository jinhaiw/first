/*******************************************************************************
 * Copyright (c) 2008, 2013 Wind River Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *     IBM Corporation - bug fixing
 *******************************************************************************/
package org.eclipse.debug.examples.core.pda.protocol;


/**
 * Clears any breakpoint set on given line
 * 
 * <pre>
 *    C: clear {line}
 *    R: ok
 * </pre>
 */
public class PDAClearBreakpointCommand extends PDACommand {

    public PDAClearBreakpointCommand(int line) {
        super("clear " + line); //$NON-NLS-1$
    }
    
    @Override
	public PDACommandResult createResult(String resultText) {
        return new PDACommandResult(resultText);
    }
}
