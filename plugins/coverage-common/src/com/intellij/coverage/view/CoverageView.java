package com.intellij.coverage.view;

import com.intellij.CommonBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunDialog;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;

/**
 * User: anna
 * Date: 1/2/12
 */
public class CoverageView extends JPanel implements DataProvider{
  @NonNls private static final String ACTION_DRILL_DOWN = "DrillDown";
  @NonNls private static final String ACTION_GO_UP = "GoUp";

  private CoverageTableModel myModel;
  private JBTable myTable;
  private CoverageViewBuilder myBuilder;
  private final Project myProject;
  private final CoverageViewManager.StateBean myStateBean;
 

  public CoverageView(final Project project, final CoverageDataManager dataManager, CoverageViewManager.StateBean stateBean) {
    super(new BorderLayout());
    myProject = project;
    myStateBean = stateBean;
    final JLabel titleLabel = new JLabel();
    final CoverageSuitesBundle suitesBundle = dataManager.getCurrentSuitesBundle();
    myModel = new CoverageTableModel(suitesBundle, stateBean, project);

    myTable = new JBTable(myModel);
    final StatusText emptyText = myTable.getEmptyText();
    emptyText.setText("No coverage results.");
    final RunConfigurationBase configuration = suitesBundle.getRunConfiguration();
    if (configuration != null) {
      emptyText.appendText(" Click ");
      emptyText.appendText("Edit", SimpleTextAttributes.LINK_ATTRIBUTES, new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final String configurationName = configuration.getName();
          final RunnerAndConfigurationSettings configurationSettings = RunManagerEx.getInstanceEx(project).findConfigurationByName(configurationName);
          if (configurationSettings != null) {
            RunDialog.editConfiguration(project, configurationSettings, "Edit Run Configuration");
          } else {
            Messages.showErrorDialog(project, "Configuration \'" + configurationName + "\' was not found", CommonBundle.getErrorTitle());
          }
        }
      });
      emptyText.appendText(" to fix configuration settings.");
    }
    myTable.getColumnModel().getColumn(0).setCellRenderer(new NodeDescriptorTableCellRenderer());
    myTable.getTableHeader().setReorderingAllowed(false);
    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    centerPanel.add(titleLabel, BorderLayout.NORTH);
    add(centerPanel, BorderLayout.CENTER);
    final CoverageViewTreeStructure structure = new CoverageViewTreeStructure(project, suitesBundle, stateBean);
    myBuilder = new CoverageViewBuilder(project, new JBList(), myModel, structure, myTable);
    myBuilder.setParentTitle(titleLabel);
    myTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          drillDown(structure);
        }
      }
    });
    final TableSpeedSearch speedSearch = new TableSpeedSearch(myTable);
    speedSearch.setClearSearchOnNavigateNoMatch(true);
    PopupHandler.installUnknownPopupHandler(myTable, createPopupGroup(), ActionManager.getInstance());
    TableScrollingUtil.installActions(myTable);

    myTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (myBuilder == null) return;
        myBuilder.buildRoot();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK), JComponent.WHEN_FOCUSED);

    myTable.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ACTION_DRILL_DOWN);
    myTable.getInputMap(WHEN_FOCUSED).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK), ACTION_DRILL_DOWN);
    myTable.getActionMap().put(ACTION_DRILL_DOWN, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        drillDown(structure);
      }
    });
    myTable.getInputMap(WHEN_FOCUSED).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK), ACTION_GO_UP);
    myTable.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), ACTION_GO_UP);
    myTable.getActionMap().put(ACTION_GO_UP, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        goUp();
      }
    });

    final JComponent component =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createToolbarActions(structure), false).getComponent();
    add(component, BorderLayout.WEST);
  }

  private static ActionGroup createPopupGroup() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    return actionGroup;
  }

  private ActionGroup createToolbarActions(final CoverageViewTreeStructure treeStructure) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new GoUpAction(treeStructure));
    if (treeStructure.supportFlattenPackages()) {
      actionGroup.add(new FlattenPackagesAction());
    }

    installAutoScrollToSource(actionGroup);
    installAutoScrollFromSource(actionGroup);

    actionGroup.add(ActionManager.getInstance().getAction("GenerateCoverageReport"));
    actionGroup.add(new CloseTabToolbarAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        CoverageDataManager.getInstance(myProject).chooseSuitesBundle(null);
      }
    });
    actionGroup.add(new ContextHelpAction("reference.toolWindows.Coverage"));
    return actionGroup;
  }

  private void installAutoScrollFromSource(DefaultActionGroup actionGroup) {
    final MyAutoScrollFromSourceHandler handler = new MyAutoScrollFromSourceHandler();
    handler.install();
    actionGroup.add(handler.createToggleAction());
  }

  private void installAutoScrollToSource(DefaultActionGroup actionGroup) {
    AutoScrollToSourceHandler autoScrollToSourceHandler = new AutoScrollToSourceHandler(){
      @Override
      protected boolean isAutoScrollMode() {
        return myStateBean.myAutoScrollToSource;
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        myStateBean.myAutoScrollToSource = state;
      }
    };
    autoScrollToSourceHandler.install(myTable);
    actionGroup.add(autoScrollToSourceHandler.createToggleAction());
  }

  public void goUp() {
    if (myBuilder == null) {
      return;
    }
    myBuilder.goUp();
  }

  private void drillDown(CoverageViewTreeStructure treeStructure) {
    final AbstractTreeNode element = getSelectedValue();
    if (element == null) return;
    if (treeStructure.getChildElements(element).length == 0) {
      if (element.canNavigate()) {
        element.navigate(true);
      }
      return;
    }
    myBuilder.drillDown();
  }

  public void updateParentTitle() {
    myBuilder.updateParentTitle();
  }
  
  private AbstractTreeNode getSelectedValue() {
    return (AbstractTreeNode)myBuilder.getSelectedValue();
  }

  private boolean topElementIsSelected(final CoverageViewTreeStructure treeStructure) {
    if (myTable == null) return false;
    if (myModel.getSize() >= 1) {
      final AbstractTreeNode rootElement = (AbstractTreeNode)treeStructure.getRootElement();
      final AbstractTreeNode node = (AbstractTreeNode)myModel.getElementAt(0);
      if (node.getParent() == rootElement) {
        return true;
      }
    }
    return false;
  }

  public boolean canSelect(VirtualFile file) {
    return myBuilder.canSelect(file);
  }

  public void select(VirtualFile file) {
    myBuilder.select(file);
  }

  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      return getSelectedValue();
    }
    return null;
  }

  private static class NodeDescriptorTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (value instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)value;
        setIcon(descriptor.getOpenIcon());
        setText(descriptor.toString());
        if (!isSelected) setForeground(((CoverageListNode)descriptor).getFileStatus().getColor());
      }
      return component;
    }
  }

  private class FlattenPackagesAction extends ToggleAction {

    private FlattenPackagesAction() {
      super("Flatten Packages", "Flatten Packages", IconLoader.getIcon("/objectBrowser/flattenPackages.png"));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myStateBean.myFlattenPackages;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myStateBean.myFlattenPackages = state;
      final Object selectedValue = myBuilder.getSelectedValue();
      myBuilder.buildRoot();

      if (selectedValue != null) {
        myBuilder.select(((CoverageListNode)selectedValue).getValue());
      }
      myBuilder.ensureSelectionExist();
      myBuilder.updateParentTitle();
    }
  }
  
  private class GoUpAction extends AnAction {

    private final CoverageViewTreeStructure myTreeStructure;

    public GoUpAction(CoverageViewTreeStructure treeStructure) {
      super("Go Up", "Go to Upper Level", IconLoader.getIcon("/nodes/upLevel.png"));
      myTreeStructure = treeStructure;
      registerCustomShortcutSet(KeyEvent.VK_BACK_SPACE, 0, myTable);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      goUp();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!topElementIsSelected(myTreeStructure));
    }
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private final Alarm myAutoscrollAlarm = new Alarm(myProject);
    
    public MyAutoScrollFromSourceHandler() {
      super(CoverageView.this.myProject);
    }

    @Override
    protected boolean isAutoScrollMode() {
      return myStateBean.myAutoScrollFromSource;
    }

    @Override
    protected void setAutoScrollMode(boolean state) {
      myStateBean.myAutoScrollFromSource = state;
    }

    public void install() {
      final MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
        public void selectionChanged(final FileEditorManagerEvent event) {
          final FileEditor newEditor = event.getNewEditor();
          if (newEditor == null) return;
          myAutoscrollAlarm.cancelAllRequests();
          myAutoscrollAlarm.addRequest(new Runnable() {
            public void run() {
              if (myProject.isDisposed() || !CoverageView.this.isShowing()) return;
              if (myStateBean.myAutoScrollFromSource) {
                final VirtualFile file = FileEditorManagerEx.getInstanceEx(myProject).getFile(newEditor);
                if (file != null) {
                  if (canSelect(file)) {
                    PsiElement e = null;
                    if (newEditor instanceof TextEditor) {
                      final int offset = ((TextEditor)newEditor).getEditor().getCaretModel().getOffset();
                      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                      final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
                      if (psiFile != null) {
                        e = psiFile.findElementAt(offset);
                      }
                    }
                    myBuilder.select(e != null ? e : file);
                  }
                }
              }
            }
          }, 300, ModalityState.NON_MODAL);
        }
      });
    }

    public void dispose() {
      if (!myAutoscrollAlarm.isDisposed()) {
        myAutoscrollAlarm.cancelAllRequests();
      }
    }
  }
}
