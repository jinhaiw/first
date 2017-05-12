/*******************************************************************************
 * Copyright (c) 2010, 2014 Wind River Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *     Dorin Ciuca - Top index fix (Bug 324100)
 *     IBM Corporation - clean-up
 *     Anton Leherbauer (Wind River) - REVEAL delta does not always work reliably (Bug 438724)
 *******************************************************************************/
package org.eclipse.debug.tests.viewer.model;

import junit.framework.TestCase;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.internal.ui.viewers.model.IInternalTreeModelViewer;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDeltaVisitor;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.PresentationContext;
import org.eclipse.debug.internal.ui.viewers.model.provisional.TreeModelViewer;
import org.eclipse.debug.tests.viewer.model.TestModel.TestElement;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * @since 3.6
 */
public class JFaceViewerTopIndexTests extends TestCase implements ITestModelUpdatesListenerConstants {

    Display fDisplay;
    Shell fShell;
    TreeModelViewer fViewer;
    TestModelUpdatesListener fListener;

    public JFaceViewerTopIndexTests(String name) {
        super(name);
    }

    /**
     * @throws java.lang.Exception
     */
    @Override
	protected void setUp() throws Exception {
        fDisplay = PlatformUI.getWorkbench().getDisplay();
        fShell = new Shell(fDisplay);
        fShell.setSize(300, 80);
        fShell.setLayout(new FillLayout());

        fViewer = createViewer(fDisplay, fShell);

        fListener = new TestModelUpdatesListener(fViewer, false, false);

        fShell.open ();
    }

    /**
     * @throws java.lang.Exception
     */
    @Override
	protected void tearDown() throws Exception {
        fListener.dispose();
        fViewer.getPresentationContext().dispose();

        // Close the shell and exit.
        fShell.close();
        while (!fShell.isDisposed()) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}
    }

    @Override
	protected void runTest() throws Throwable {
        try {
            super.runTest();
        } catch (Throwable t) {
			throw new ExecutionException("Test failed: " + t.getMessage() + "\n fListener = " + fListener.toString(), t); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    protected IInternalTreeModelViewer getCTargetViewer() {
        return fViewer;
    }

    /**
     * @param display the display
     * @param shell the shell
     * @return the new viewer
     */
    protected TreeModelViewer createViewer(Display display, Shell shell) {
		return new TreeModelViewer(fShell, SWT.VIRTUAL | SWT.MULTI, new PresentationContext("TestViewer")); //$NON-NLS-1$
    }

    /**
     * Restore REVEAL on simple model with elements without children.
     *
     */
    public void testRestoreTopIndex() throws InterruptedException {
        TreeModelViewerAutopopulateAgent autopopulateAgent = new TreeModelViewerAutopopulateAgent(fViewer);

    	TestModel model = new TestModel();

        TestElement[] elements = new TestElement[8];
        for (int i = 0; i < elements.length; i++) {
            String text = Integer.toString(i + 1);
        	// elements don't have children
        	elements[i] =
                new TestElement(model, text, new TestElement[0] );

        }
		model.setRoot(new TestElement(model, "root", elements)); //$NON-NLS-1$

        // Create the listener, only check the first level
        fListener.reset(TreePath.EMPTY, model.getRootElement(), 1, false, false);

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());

        while (!fListener.isFinished()) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}
        model.validateData(fViewer, TreePath.EMPTY, true);

        // Stop forcing view updates.
        autopopulateAgent.dispose();

        // scroll to the 5th element
        int indexRevealElem = 4;
        getCTargetViewer().reveal(TreePath.EMPTY, indexRevealElem);
        while(fDisplay.readAndDispatch()) {}
        final TreePath originalTopPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", originalTopPath); //$NON-NLS-1$
        // Bug 116105: On a Mac the reveal call is not reliable.  Use the viewer returned path instead.
        // assertEquals(elements[indexRevealElem], originalTopPath.getLastSegment());

        // Extract the original state from viewer
        ModelDelta originalState = new ModelDelta(model.getRootElement(), IModelDelta.NO_CHANGE);
        fViewer.saveElementState(TreePath.EMPTY, originalState, IModelDelta.EXPAND | IModelDelta.SELECT);

        // Set the viewer input to null.  This will trigger the view to save the viewer state.
        fListener.reset(true, false);
        fListener.addStateUpdates(getCTargetViewer(), originalState, IModelDelta.EXPAND | IModelDelta.SELECT | IModelDelta.REVEAL);

        fViewer.setInput(null);
        while (!fListener.isFinished(STATE_SAVE_COMPLETE | STATE_UPDATES)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        // Set the viewer input back to the model to trigger RESTORE operation.
        fListener.reset(false, false);
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished(ALL_UPDATES_COMPLETE | STATE_RESTORE_COMPLETE)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        while (fDisplay.readAndDispatch ()) {}
        // check if REVEAL was restored OK
        final TreePath topPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", topPath); //$NON-NLS-1$
        TreePathWrapper.assertEqual(originalTopPath, topPath);
    }

    /**
     * Restore REVEAL when having also to restore an expanded element
     * that is just above the REVEAL element.
     *
     * See bug 324100
     */
    public void testRestoreTopAndExpand() throws InterruptedException {
        TreeModelViewerAutopopulateAgent autopopulateAgent = new TreeModelViewerAutopopulateAgent(fViewer);

        TestModel model = new TestModel();

        TestElement[] elements = new TestElement[10];
        for (int i = 0; i < elements.length; i++) {
            String text = Integer.toString(i + 1);
            // first element has 2 children
            if (i == 0) {
            	elements[i] =
                    new TestElement(model, text, new TestElement[] {
 new TestElement(model, text + ".1", new TestElement[0]), //$NON-NLS-1$
				new TestElement(model, text + ".2", new TestElement[0]) //$NON-NLS-1$
                    });
            } else {
            	// rest of elements don't have children
            	elements[i] =
                    new TestElement(model, text, new TestElement[0] );
            }

        }
		model.setRoot(new TestElement(model, "root", elements)); //$NON-NLS-1$

        // Create the listener, only check the first level
        fListener.reset(TreePath.EMPTY, model.getRootElement(), 1, false, false);

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());

        while (!fListener.isFinished()) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}
        model.validateData(fViewer, TreePath.EMPTY, true);

        // Expand first element
        fListener.reset();
        fListener.setFailOnRedundantUpdates(false);
        int indexFirstElem = 0;
        TestElement firstElem = elements[indexFirstElem];
        ModelDelta rootDelta = new ModelDelta(model.getRootElement(), IModelDelta.NO_CHANGE);
        ModelDelta delta = model.getBaseDelta(rootDelta);
        TreePath firstElemPath = model.findElement(firstElem.getLabel());
        fListener.addUpdates(
            firstElemPath, firstElem, 1,
            CHILD_COUNT_UPDATES | CHILDREN_UPDATES );
        delta.addNode(firstElem, indexFirstElem, IModelDelta.EXPAND, firstElem.getChildren().length);

        model.postDelta(rootDelta);

        while (!fListener.isFinished(CONTENT_SEQUENCE_COMPLETE | MODEL_CHANGED_COMPLETE)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        // Validate that the first node is expanded
        assertTrue(getCTargetViewer().getExpandedState(firstElemPath) == true);

        // Stop forcing view updates.
        autopopulateAgent.dispose();

        // scroll to the 2nd element
        getCTargetViewer().reveal(TreePath.EMPTY, 1);
        while(fDisplay.readAndDispatch()) {}
        final TreePath originalTopPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", originalTopPath); //$NON-NLS-1$
        // Bug 116105: On a Mac the reveal call is not reliable.  Use the viewer returned path instead.
        //assertEquals(elements[1], originalTopPath.getLastSegment());

        // Extract the original state from viewer
        ModelDelta originalState = new ModelDelta(model.getRootElement(), IModelDelta.NO_CHANGE);
        fViewer.saveElementState(TreePath.EMPTY, originalState, IModelDelta.EXPAND | IModelDelta.SELECT);

        // Set the viewer input to null.  This will trigger the view to save the viewer state.
        fListener.reset(true, false);
        fListener.addStateUpdates(getCTargetViewer(), originalState, IModelDelta.EXPAND | IModelDelta.SELECT | IModelDelta.REVEAL);
        fViewer.setInput(null);
        while (!fListener.isFinished(STATE_SAVE_COMPLETE)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        // Set the viewer input back to the model
        fListener.reset(false, false);
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished(ALL_UPDATES_COMPLETE | STATE_RESTORE_COMPLETE)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        while (fDisplay.readAndDispatch ()) {}
        // check if REVEAL was restored OK
        final TreePath topPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", topPath); //$NON-NLS-1$
        TreePathWrapper.assertEqual(originalTopPath, topPath);
    }

    /**
     * Restore REVEAL when this operation triggers restoring of an expanded
     * element.
     *
     * See bug 324100
     */
    public void testRestoreTopTriggersExpand() throws InterruptedException {
        TreeModelViewerAutopopulateAgent autopopulateAgent = new TreeModelViewerAutopopulateAgent(fViewer);

        TestModel model = new TestModel();

        TestElement[] elements = new TestElement[10];
        for (int i = 0; i < elements.length; i++) {
            String text = Integer.toString(i + 1);
            // last element has 2 children
            if (i == elements.length - 1) {
            	elements[i] =
                    new TestElement(model, text, new TestElement[] {
 new TestElement(model, text + ".1", new TestElement[0]), //$NON-NLS-1$
				new TestElement(model, text + ".2", new TestElement[0]) //$NON-NLS-1$
                    });
            } else {
            	// rest of elements don't have children
            	elements[i] =
                    new TestElement(model, text, new TestElement[0] );
            }

        }

        fViewer.setAutoExpandLevel(-1);
		model.setRoot(new TestElement(model, "root", elements)); //$NON-NLS-1$

        // Create the listener, only check the first level
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false);

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());

        while (!fListener.isFinished()) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}
        model.validateData(fViewer, TreePath.EMPTY, true);

        int indexLastElem = elements.length-1;
        TestElement lastElem = elements[indexLastElem];
        TreePath lastElemePath = model.findElement(lastElem.getLabel());

        // Validate that the last node is expanded
        assertTrue(getCTargetViewer().getExpandedState(lastElemePath) == true);

        // Stop forcing view updates.
        fViewer.setAutoExpandLevel(0);
        autopopulateAgent.dispose();

        // scroll to the element before last element
        getCTargetViewer().reveal(TreePath.EMPTY, indexLastElem-1);
        while(fDisplay.readAndDispatch()) {}
        final TreePath originalTopPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", originalTopPath); //$NON-NLS-1$

        // Extract the original state from viewer
        ModelDelta originalState = new ModelDelta(model.getRootElement(), IModelDelta.NO_CHANGE);
        fViewer.saveElementState(TreePath.EMPTY, originalState, IModelDelta.EXPAND | IModelDelta.SELECT);

        // Set the viewer input to null.  This will trigger the view to save the viewer state.
        fListener.reset(true, false);
        fListener.addStateUpdates(getCTargetViewer(), originalState, IModelDelta.EXPAND | IModelDelta.SELECT | IModelDelta.REVEAL);

        fViewer.setInput(null);
        while (!fListener.isFinished(STATE_SAVE_COMPLETE | STATE_UPDATES)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        // Set the viewer input back to the model.
        fListener.reset(false, false);
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished(ALL_UPDATES_COMPLETE | STATE_RESTORE_COMPLETE)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        while (fDisplay.readAndDispatch ()) {}
        // check if REVEAL was restored OK
        final TreePath topPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", topPath); //$NON-NLS-1$
        TreePathWrapper.assertEqual(originalTopPath, topPath);
    }

    /**
     * Test for bug 326965.<br>
     * This test verifies that canceling a reveal pending state delta is
     * properly handled when a new reveal delta is received from the model.
     */
    public void testRestoreRevealAfterRevealCancel() throws InterruptedException {
        TreeModelViewerAutopopulateAgent autopopulateAgent = new TreeModelViewerAutopopulateAgent(fViewer);
        TestModel model = TestModel.simpleMultiLevel();

        // Expand all
        fViewer.setAutoExpandLevel(-1);

        // Create the listener.
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false);

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}
        model.validateData(fViewer, TreePath.EMPTY, true);

        // Stop autopopulating the view.
        autopopulateAgent.dispose();

        // Set top index of view to element "3" and wait for view to repaint.
        getCTargetViewer().reveal(TreePath.EMPTY, 2);
        while(fDisplay.readAndDispatch()) {}

        // Trigger save of state.
        fListener.reset();
        fViewer.setInput(null);
        while (!fListener.isFinished(STATE_SAVE_COMPLETE)) {
			Thread.sleep(0);
		}

        // Set input back to root element.
        // Note: Wait only for the processing of the delta and the start of state restore, not for all updates
        fListener.reset();
		TreePath elementPath = model.findElement("3"); //$NON-NLS-1$
        fListener.addUpdates(fViewer, elementPath, model.getElement(elementPath), 1, STATE_UPDATES);
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | STATE_UPDATES)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        // Update the viewer with new selection delta to something new in the view
		ModelDelta revealDelta = model.makeElementDelta(model.findElement("2.1"), IModelDelta.REVEAL); //$NON-NLS-1$

        // Wait for the second model delta to process
        fListener.reset();
        model.postDelta(revealDelta);
        while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CONTENT_SEQUENCE_COMPLETE)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        // Clear view then reset it again.
        fListener.reset();
        fViewer.setInput(null);
        while (!fListener.isFinished(STATE_SAVE_COMPLETE)) {
			Thread.sleep(0);
		}

        autopopulateAgent = new TreeModelViewerAutopopulateAgent(fViewer);
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished(STATE_RESTORE_COMPLETE)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}
        autopopulateAgent.dispose();
    }

    /**
     * Test for bug 326965.<br>
     * This test verifies that canceling a reveal pending state delta is
     * properly handled when a new reveal delta is received from the model.
     */
    public void testRestoreRevealAfterRevealCancel2() throws InterruptedException {
    	if (Platform.getOS().equals(Platform.OS_MACOSX)) {
    		// skip this test on Mac - see bug 327557
    		return;
    	}
        TreeModelViewerAutopopulateAgent autopopulateAgent = new TreeModelViewerAutopopulateAgent(fViewer);
        TestModel model = TestModel.simpleMultiLevel();

        // Expand all
        fViewer.setAutoExpandLevel(-1);

        // Create the listener.
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false);

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}
        model.validateData(fViewer, TreePath.EMPTY, true);

        // Stop auto-populating and auto-expanding the view.
        fViewer.setAutoExpandLevel(0);
        autopopulateAgent.dispose();

        // Set top index of view to element "3" and wait for view to repaint.
        getCTargetViewer().reveal(TreePath.EMPTY, 2);
        while(fDisplay.readAndDispatch()) {}

        // Trigger save of state.
        fListener.reset();
        fViewer.setInput(null);
        while (!fListener.isFinished(STATE_SAVE_COMPLETE)) {
			Thread.sleep(0);
		}

        // Set input back to root element.
        // Note: Wait only for the processing of the delta and the start of state restore, not for all updates
        fListener.reset();
		TreePath elementPath = model.findElement("2"); //$NON-NLS-1$
        fListener.addUpdates(fViewer, elementPath, model.getElement(elementPath), 1, STATE_UPDATES | CHILDREN_UPDATES | LABEL_UPDATES);
		elementPath = model.findElement("3"); //$NON-NLS-1$
        fListener.addUpdates(fViewer, elementPath, model.getElement(elementPath), 0, STATE_UPDATES);
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished(STATE_UPDATES)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        // Update the viewer with new selection delta to something new in the view
		TreePath pathToBeRevealed = model.findElement("2.1"); //$NON-NLS-1$
        ModelDelta revealDelta = model.makeElementDelta(pathToBeRevealed, IModelDelta.REVEAL);
        revealDelta.accept(new IModelDeltaVisitor() {

            @Override
			public boolean visit(IModelDelta delta, int depth) {
                ((ModelDelta)delta).setFlags(delta.getFlags() | IModelDelta.EXPAND);
                return true;
            }
        });

        // Wait for the second model delta to process
        model.postDelta(revealDelta);
        while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CHILDREN_UPDATES | LABEL_UPDATES)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        // check if REVEAL was triggered by the delta and not by the
        // state restore operation
        TreePath topPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", topPath); //$NON-NLS-1$
        TreePathWrapper.assertEqual(pathToBeRevealed, topPath);
    }



    /**
     * Restore REVEAL when having also to restore an expanded element
     * that is just above the REVEAL element.
     *
     * See bug 324100
     */
    public void testRestoreDeepTreeAndReveal() throws InterruptedException {
        TreeModelViewerAutopopulateAgent autopopulateAgent = new TreeModelViewerAutopopulateAgent(fViewer);

        TestModel model = TestModel.simpleDeepMultiLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener, only check the first level
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false);


        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());

        while (!fListener.isFinished()) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}
        model.validateData(fViewer, TreePath.EMPTY, true);

        // Stop forcing view updates.
        autopopulateAgent.dispose();

        // Scroll down to the last part of the tree.
		getCTargetViewer().reveal(model.findElement("3.6.3.16.16.16.16.16"), 1); //$NON-NLS-1$
        while(fDisplay.readAndDispatch()) {}
        final TreePath originalTopPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", originalTopPath); //$NON-NLS-1$

        // Extract the original state from viewer
        ModelDelta originalState = new ModelDelta(model.getRootElement(), IModelDelta.NO_CHANGE);
        fViewer.saveElementState(TreePath.EMPTY, originalState, IModelDelta.EXPAND | IModelDelta.SELECT);

        // Set the viewer input to null.  This will trigger the view to save the viewer state.
        fListener.reset(true, false);
        fListener.addStateUpdates(getCTargetViewer(), originalState, IModelDelta.EXPAND | IModelDelta.SELECT | IModelDelta.REVEAL);
        fViewer.setInput(null);
        while (!fListener.isFinished(STATE_SAVE_COMPLETE)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        // Set the viewer input back to the model
        fListener.reset(false, false);
        fListener.addUpdates(getCTargetViewer(), originalTopPath, (TestElement)originalTopPath.getLastSegment(), 0, STATE_UPDATES);
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished(STATE_UPDATES | CONTENT_SEQUENCE_COMPLETE)) {
			if (!fDisplay.readAndDispatch ()) {
				Thread.sleep(0);
			}
		}

        while (fDisplay.readAndDispatch ()) {}
        // check if REVEAL was restored OK
        final TreePath topPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", topPath); //$NON-NLS-1$
        TreePathWrapper.assertEqual(originalTopPath, topPath);

    }

	/**
	 * This test verifies that a revealed node does not get scrolled away due to
	 * structural updates.
	 */
	public void testRevealWithContentChanges() throws InterruptedException {
		@SuppressWarnings("unused")
		TreeModelViewerAutopopulateAgent autopopulateAgent = new TreeModelViewerAutopopulateAgent(fViewer);
		TestModel model = TestModel.simpleDeepMultiLevel();

		// Expand first level
		fViewer.setAutoExpandLevel(1);

		// Create the listener.
		fListener.reset(false, false);

		// Set the input into the view and update the view.
		fViewer.setInput(model.getRootElement());
		while (!fListener.isFinished()) {
			if (!fDisplay.readAndDispatch()) {
				Thread.sleep(0);
			}
		}
		model.validateData(fViewer, TreePath.EMPTY, true);

		// Set top index of view to element "2" and wait for view to repaint.
		getCTargetViewer().reveal(TreePath.EMPTY, 1);
		while (fDisplay.readAndDispatch()) {
		}
		TreePath element2Path = model.findElement("2"); //$NON-NLS-1$
		TreePath pathToBeRevealed = element2Path;
		TreePath topPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", topPath); //$NON-NLS-1$
		TreePathWrapper.assertEqual(pathToBeRevealed, topPath);

		// Update the viewer with new reveal delta
		pathToBeRevealed = model.findElement("3"); //$NON-NLS-1$
		ModelDelta revealDelta = model.makeElementDelta(pathToBeRevealed, IModelDelta.REVEAL);
		// Add CONTENT delta for model element "2"
		ModelDelta element2Delta = model.makeElementDelta(element2Path, IModelDelta.CONTENT);
		element2Delta = (ModelDelta) element2Delta.getChildDeltas()[0];
		revealDelta.addNode(element2Delta.getElement(), element2Delta.getIndex(), element2Delta.getFlags(), element2Delta.getChildCount());

		// Remove some children from element "2"
		model.removeElementChild(element2Path, 0);
		model.removeElementChild(element2Path, 0);

		fListener.reset();

		// Delay updates
		model.setQeueueingUpdate(true);

		// Wait for the model delta to process
		model.postDelta(revealDelta);
		while (!fListener.isFinished(CHILD_COUNT_UPDATES_STARTED)) {
			if (!fDisplay.readAndDispatch()) {
				Thread.sleep(0);
			}
		}

		model.setQeueueingUpdate(false);

		while (!fListener.isFinished(ALL_UPDATES_COMPLETE)) {
			if (!fDisplay.readAndDispatch()) {
				Thread.sleep(0);
			}
		}

		// check if REVEAL actually revealed the desired element
		topPath = getCTargetViewer().getTopElementPath();
		assertNotNull("Top item should not be null!", topPath); //$NON-NLS-1$
		TreePathWrapper.assertEqual(pathToBeRevealed, topPath);
	}

}
