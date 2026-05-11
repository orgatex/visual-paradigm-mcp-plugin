#!/bin/bash
# Generate minimal VP API stub JAR for Docker compilation
# This creates a stub with just enough VP API classes for the code to compile.

set -e

STUB_DIR=$(mktemp -d)
CLASSES_DIR="$STUB_DIR/classes"
SRC_DIR="$STUB_DIR/src"

mkdir -p "$CLASSES_DIR" "$SRC_DIR/com/vp/plugin" "$SRC_DIR/com/vp/plugin/model" \
    "$SRC_DIR/com/vp/plugin/model/factory" "$SRC_DIR/com/vp/plugin/diagram" \
    "$SRC_DIR/com/vp/plugin/action"

# VPPlugin
cat > "$SRC_DIR/com/vp/plugin/VPPluginInfo.java" << 'EOF'
package com.vp.plugin;
public interface VPPluginInfo { String getPluginId(); }
EOF

cat > "$SRC_DIR/com/vp/plugin/VPPlugin.java" << 'EOF'
package com.vp.plugin;
public interface VPPlugin { void loaded(VPPluginInfo info); void unloaded(); }
EOF

# ApplicationManager
cat > "$SRC_DIR/com/vp/plugin/ApplicationManager.java" << 'EOF'
package com.vp.plugin;
public abstract class ApplicationManager {
    public static ApplicationManager instance() { return null; }
    public abstract DiagramManager getDiagramManager();
    public abstract ProjectManager getProjectManager();
    public abstract ViewManager getViewManager();
}
EOF

# DiagramManager
cat > "$SRC_DIR/com/vp/plugin/DiagramManager.java" << 'EOF'
package com.vp.plugin;
public abstract class DiagramManager {
    public static final int LAYOUT_AUTO = 0;
    public static final int LAYOUT_HIERARCHIC = 1;
    public static final int LAYOUT_ORGANIC = 2;
    public abstract com.vp.plugin.diagram.IDiagramUIModel createDiagram(String type);
    public abstract void openDiagram(com.vp.plugin.diagram.IDiagramUIModel d);
    public abstract com.vp.plugin.diagram.IDiagramElement createDiagramElement(com.vp.plugin.diagram.IDiagramUIModel d, com.vp.plugin.model.IModelElement m);
    public abstract com.vp.plugin.diagram.IDiagramElement createConnector(com.vp.plugin.diagram.IDiagramUIModel d, com.vp.plugin.model.IModelElement m, com.vp.plugin.diagram.IDiagramElement from, com.vp.plugin.diagram.IDiagramElement to, java.awt.Point[] pts);
    public abstract com.vp.plugin.diagram.IDiagramElement createConnector(com.vp.plugin.diagram.IDiagramUIModel d, String connectorType, com.vp.plugin.diagram.IDiagramElement from, com.vp.plugin.diagram.IDiagramElement to, java.awt.Point[] pts);
    public abstract void autoLayout(com.vp.plugin.diagram.IDiagramUIModel d, int layoutType);
    public abstract void layout(com.vp.plugin.diagram.IDiagramUIModel d, Object option);
    public abstract com.vp.plugin.diagram.IDiagramUIModel getActiveDiagram();
    public abstract com.vp.plugin.diagram.IDiagramUIModel[] getDiagrams(String type);
}
EOF

# ProjectManager / ViewManager
cat > "$SRC_DIR/com/vp/plugin/ProjectManager.java" << 'EOF'
package com.vp.plugin;
public abstract class ProjectManager { public abstract com.vp.plugin.model.IProject getProject(); }
EOF

cat > "$SRC_DIR/com/vp/plugin/ViewManager.java" << 'EOF'
package com.vp.plugin;
public abstract class ViewManager { public abstract javax.swing.Icon getIconByModelType(String t); }
EOF

# Model interfaces
cat > "$SRC_DIR/com/vp/plugin/model/IModelElement.java" << 'EOF'
package com.vp.plugin.model;
public interface IModelElement { String getName(); void setName(String name); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IProject.java" << 'EOF'
package com.vp.plugin.model;
public interface IProject extends IModelElement {
    java.util.Iterator<?> allLevelModelElementIterator();
    java.util.Iterator<?> diagramIterator();
}
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IClass.java" << 'EOF'
package com.vp.plugin.model;
public interface IClass extends IModelElement {
    void addAttribute(IAttribute a);
    void addOperation(IOperation o);
    void addStereotype(String s);
    java.util.Iterator<?> attributeIterator();
    java.util.Iterator<?> operationIterator();
}
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IAttribute.java" << 'EOF'
package com.vp.plugin.model;
public interface IAttribute extends IModelElement { void setType(String t); void setVisibility(String v); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IParameter.java" << 'EOF'
package com.vp.plugin.model;
public interface IParameter extends IModelElement { void setType(String t); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IOperation.java" << 'EOF'
package com.vp.plugin.model;
public interface IOperation extends IModelElement { void setReturnType(String t); void addParameter(IParameter p); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IActor.java" << 'EOF'
package com.vp.plugin.model;
public interface IActor extends IModelElement {}
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IUseCase.java" << 'EOF'
package com.vp.plugin.model;
public interface IUseCase extends IModelElement {}
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IInclude.java" << 'EOF'
package com.vp.plugin.model;
public interface IInclude extends IModelElement { void setFrom(IModelElement f); void setTo(IModelElement t); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IExtend.java" << 'EOF'
package com.vp.plugin.model;
public interface IExtend extends IModelElement { void setFrom(IModelElement f); void setTo(IModelElement t); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IAssociation.java" << 'EOF'
package com.vp.plugin.model;
public interface IAssociation extends IModelElement { void setFrom(IModelElement f); void setTo(IModelElement t); IAssociationEnd getFromEnd(); IAssociationEnd getToEnd(); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IAssociationEnd.java" << 'EOF'
package com.vp.plugin.model;
public interface IAssociationEnd extends IModelElement {
    int AGGREGATION_KIND_AGGREGATION = 1;
    int AGGREGATION_KIND_COMPOSITED = 2;
    void setAggregationKind(int k);
    void setMultiplicity(String m);
}
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IGeneralization.java" << 'EOF'
package com.vp.plugin.model;
public interface IGeneralization extends IModelElement { void setFrom(IModelElement f); void setTo(IModelElement t); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IDependency.java" << 'EOF'
package com.vp.plugin.model;
public interface IDependency extends IModelElement { void setFrom(IModelElement f); void setTo(IModelElement t); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IRealization.java" << 'EOF'
package com.vp.plugin.model;
public interface IRealization extends IModelElement { void setFrom(IModelElement f); void setTo(IModelElement t); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IDBTable.java" << 'EOF'
package com.vp.plugin.model;
public interface IDBTable extends IModelElement { void addDBColumn(IDBColumn c); java.util.Iterator<?> dBColumnIterator(); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IDBColumn.java" << 'EOF'
package com.vp.plugin.model;
public interface IDBColumn extends IModelElement {
    boolean setType(String t, int length, int scale);
    void setPrimaryKey(boolean pk);
    void setNullable(boolean n);
    String getTypeInText();
    int getLength();
    int getScale();
    boolean isPrimaryKey();
    boolean isNullable();
}
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IDBForeignKey.java" << 'EOF'
package com.vp.plugin.model;
public interface IDBForeignKey extends IModelElement { void setFrom(IModelElement f); void setTo(IModelElement t); void setFromMultiplicity(String m); void setToMultiplicity(String m); void setIdentifying(boolean i); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IInteractionLifeLine.java" << 'EOF'
package com.vp.plugin.model;
public interface IInteractionLifeLine extends IModelElement { void setBaseClassifier(IClass c); void addActivation(IActivation a); java.util.Iterator<?> activationIterator(); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IActivation.java" << 'EOF'
package com.vp.plugin.model;
public interface IActivation extends IModelElement {}
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IMessage.java" << 'EOF'
package com.vp.plugin.model;
public interface IMessage extends IModelElement {
    void setSequenceNumber(String n);
    void setAsynchronous(boolean async);
    void setFromActivation(IActivation a);
    void setToActivation(IActivation a);
}
EOF

cat > "$SRC_DIR/com/vp/plugin/model/ICombinedFragment.java" << 'EOF'
package com.vp.plugin.model;
public interface ICombinedFragment extends IModelElement {
    String INTERACTION_OPERATOR_ALT = "alt";
    String INTERACTION_OPERATOR_OPT = "opt";
    String INTERACTION_OPERATOR_LOOP = "loop";
    String INTERACTION_OPERATOR_BREAK = "break";
    String INTERACTION_OPERATOR_PAR = "par";
    void setInteractionOperator(String op);
    void addOperand(IInteractionOperand o);
    void addCoveredLifeLine(IModelElement l);
}
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IInteractionOperand.java" << 'EOF'
package com.vp.plugin.model;
public interface IInteractionOperand extends IModelElement { void setGuard(IInteractionConstraint c); }
EOF

cat > "$SRC_DIR/com/vp/plugin/model/IInteractionConstraint.java" << 'EOF'
package com.vp.plugin.model;
public interface IInteractionConstraint extends IModelElement { void setConstraint(String c); }
EOF

# Factory
cat > "$SRC_DIR/com/vp/plugin/model/factory/IModelElementFactory.java" << 'EOF'
package com.vp.plugin.model.factory;
import com.vp.plugin.model.*;
public abstract class IModelElementFactory {
    public static IModelElementFactory instance() { return null; }
    public abstract IActor createActor();
    public abstract IUseCase createUseCase();
    public abstract IInclude createInclude();
    public abstract IExtend createExtend();
    public abstract IClass createClass();
    public abstract IAttribute createAttribute();
    public abstract IParameter createParameter();
    public abstract IOperation createOperation();
    public abstract IAssociation createAssociation();
    public abstract IGeneralization createGeneralization();
    public abstract IDependency createDependency();
    public abstract IRealization createRealization();
    public abstract IDBTable createDBTable();
    public abstract IDBColumn createDBColumn();
    public abstract IDBForeignKey createDBForeignKey();
    public abstract IInteractionLifeLine createInteractionLifeLine();
    public abstract IActivation createActivation();
    public abstract IMessage createMessage();
    public abstract ICombinedFragment createCombinedFragment();
    public abstract IInteractionOperand createInteractionOperand();
    public abstract IInteractionConstraint createInteractionConstraint();
}
EOF

# Diagram interfaces
cat > "$SRC_DIR/com/vp/plugin/diagram/IDiagramUIModel.java" << 'EOF'
package com.vp.plugin.diagram;
public interface IDiagramUIModel extends com.vp.plugin.model.IModelElement {
    java.util.Iterator<?> diagramElementIterator();
    IDiagramElement[] toDiagramElementArray();
    int diagramElementCount();
    IDiagramElement getDiagramElementById(String id);
    IDiagramElement[] getDiagramElementsByName(String name);
    IDiagramElement getDiagramElementByName(String name, boolean includeSubDiagram);
    void addDiagramElement(IDiagramElement element);
    void removeDiagramElement(IDiagramElement element);
    String getType();
    int getX();
    int getY();
    int getWidth();
    int getHeight();
}
EOF

cat > "$SRC_DIR/com/vp/plugin/diagram/IDiagramElement.java" << 'EOF'
package com.vp.plugin.diagram;
public interface IDiagramElement {
    com.vp.plugin.model.IModelElement getModelElement();
    void setModelElement(com.vp.plugin.model.IModelElement m);
    void setBounds(int x, int y, int w, int h);
    int getX();
    int getY();
    int getWidth();
    int getHeight();
    void setX(int x);
    void setY(int y);
    void setWidth(int w);
    void setHeight(int h);
    void setLocation(int x, int y);
    void setSize(int w, int h);
}
EOF

cat > "$SRC_DIR/com/vp/plugin/diagram/IDiagramTypeConstants.java" << 'EOF'
package com.vp.plugin.diagram;
public interface IDiagramTypeConstants {
    String DIAGRAM_TYPE_USE_CASE_DIAGRAM = "UseCaseDiagram";
    String DIAGRAM_TYPE_CLASS_DIAGRAM = "ClassDiagram";
    String DIAGRAM_TYPE_INTERACTION_DIAGRAM = "SequenceDiagram";
    String DIAGRAM_TYPE_ER_DIAGRAM = "ERDiagram";
    String DIAGRAM_TYPE_ACTIVITY_DIAGRAM = "ActivityDiagram";
}
EOF

cat > "$SRC_DIR/com/vp/plugin/diagram/IUseCaseDiagramUIModel.java" << 'EOF'
package com.vp.plugin.diagram;
public interface IUseCaseDiagramUIModel extends IDiagramUIModel {}
EOF

cat > "$SRC_DIR/com/vp/plugin/diagram/IClassDiagramUIModel.java" << 'EOF'
package com.vp.plugin.diagram;
public interface IClassDiagramUIModel extends IDiagramUIModel {}
EOF

cat > "$SRC_DIR/com/vp/plugin/diagram/IInteractionDiagramUIModel.java" << 'EOF'
package com.vp.plugin.diagram;
public interface IInteractionDiagramUIModel extends IDiagramUIModel {}
EOF

# Action interfaces
cat > "$SRC_DIR/com/vp/plugin/action/VPAction.java" << 'EOF'
package com.vp.plugin.action;
public interface VPAction { void setEnabled(boolean e); void setIcon(javax.swing.Icon i); }
EOF

cat > "$SRC_DIR/com/vp/plugin/action/VPActionController.java" << 'EOF'
package com.vp.plugin.action;
public interface VPActionController { void performAction(VPAction a); void update(VPAction a); }
EOF

# Compile
javac -d "$CLASSES_DIR" $(find "$SRC_DIR" -name "*.java") 2>/dev/null

# Create JAR
OUTPUT_DIR="${1:-.}"
mkdir -p "$OUTPUT_DIR"
jar cf "$OUTPUT_DIR/openapi.jar" -C "$CLASSES_DIR" .

# Cleanup
rm -rf "$STUB_DIR"

echo "Generated stub JAR: $OUTPUT_DIR/openapi.jar"
