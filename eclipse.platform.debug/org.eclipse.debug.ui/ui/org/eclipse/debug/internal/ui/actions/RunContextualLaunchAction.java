/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.actions;

import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.actions.ContextualLaunchAction;

/**
 * Specialization of <code>ContextualLaunchAction</code> for the run mode
 * 
 * @see {@link ContextualLaunchAction}
 */
public class RunContextualLaunchAction extends ContextualLaunchAction {
	
	/**
	 * Constructor
	 */
	public RunContextualLaunchAction() {
		super(ILaunchManager.RUN_MODE);
	}
}
