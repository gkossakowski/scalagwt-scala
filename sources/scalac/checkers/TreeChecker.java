/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.checkers;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import ch.epfl.lamp.util.Position;

import scalac.Unit;
import scalac.ast.Tree;
import scalac.ast.Tree.AbsTypeDef;
import scalac.ast.Tree.Template;
import scalac.ast.Tree.ValDef;
import scalac.symtab.Definitions;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.util.Debug;

/**
 * This checker checks that trees are well-formed. It checks both the
 * shape and the attribution of the trees. WARNING: currently, the
 * checks are valid only for trees generated by phase AddConstructors.
 */
public class TreeChecker {

    //########################################################################
    // Private Fields

    /** The global definitions */
    private final Definitions definitions;

    /** The stack of current units */
    private final Stack/*<Unit>*/ units = new Stack();

    /** The stack of current classes */
    private final Stack/*<Symbol>*/ classes = new Stack();

    /** The stack of current members */
    private final Stack/*<Symbol>*/ members = new Stack();

    /** The stack of current owners */
    private final Stack/*<Symbol>*/ owners = new Stack();

    /** The currently visible type variables */
    private final Set/*<Symbol>*/ tvars = new HashSet();

    /** The currently visible value variables */
    private final Set/*<Symbol>*/ vvars = new HashSet();

    /** The currently visible labels */
    private final Set/*<Symbol>*/ labels = new HashSet();

    /** The currently defined symbols */
    private final Set/*<Symbol>*/ symbols = new HashSet();

    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
    public TreeChecker(Definitions definitions) {
        this.definitions = definitions;
    }

    //########################################################################
    // Public Methods - Checking units

    /** Checks the unit. Returns true. */
    public boolean check(Unit unit) {
        pushUnit(unit);
        template(unit.body);
        popUnit();
        return true;
    }

    //########################################################################
    // Private Methods - Checking templates

    /** Checks the templates. Returns true. */
    private boolean template(Tree[] trees) {
        for (int i = 0; i < trees.length; i++) template(trees[i]);
        return true;
    }

    /** Checks the template. Returns true. */
    private boolean template(Tree tree) {
        switch (tree) {

        case Empty:
            return true;

        case ClassDef(_, _, AbsTypeDef[] tparams, ValDef[][] vparams, _, Template(_, Tree[] body)):
            Symbol symbol = tree.symbol();
            assert symbol != null && symbol.isClass(): show(tree);
            assert vparams.length == 1: show(tree);
            assert containSymbols(tparams, symbol.typeParams()): show(tree);
            assert containSymbols(vparams[0],symbol.valueParams()): show(tree);
            registerSymbol(symbol);
            scopeInsertParametersOf(symbol);
            pushClass(symbol);
            member(body);
            popClass();
            scopeRemoveParametersOf(symbol);
            return true;

        case PackageDef(Tree packaged, Template(Tree[] bases, Tree[] body)):
            Symbol symbol = packaged.symbol();
            assert symbol != null && symbol.isPackage(): show(packaged);
            assert bases.length == 0: show(tree);
            pushOwner(symbol);
            template(body);
            popOwner();
            return true;

        case ValDef(_, _, _, Tree rhs):
            Symbol symbol = tree.symbol();
            assert symbol != null && symbol.isModule(): show(tree);
            pushOwner(symbol);
            expression(rhs, symbol.type());
            popOwner();
            return true;

        default:
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Checking members

    /** Checks the members. Returns true. */
    private boolean member(Tree[] trees) {
        for (int i = 0; i < trees.length; i++) member(trees[i]);
        return true;
    }

    /** Checks the member. Returns true. */
    private boolean member(Tree tree) {
        switch (tree) {

        case Empty:
            return true;

        case ClassDef(_, _, _, _, _, _):
            return template(tree);

        case ValDef(_, _, _, Tree rhs):
            Symbol symbol = tree.symbol();
            assert symbol != null && symbol.isTerm(): show(tree);
            assert rhs == Tree.Empty: show(tree);
            registerSymbol(symbol);
            pushMember(symbol);
            popMember();
            return true;

        case DefDef(_, _, AbsTypeDef[]tparams, ValDef[][]vparams, _, Tree rhs):
            Symbol symbol = tree.symbol();
            assert symbol != null && symbol.isMethod(): show(tree);
            assert vparams.length == 1: show(tree);
            assert containSymbols(tparams, symbol.typeParams()): show(tree);
            assert containSymbols(vparams[0],symbol.valueParams()): show(tree);
            assert symbol.isDeferred() == (rhs == Tree.Empty): show(tree);
            registerSymbol(symbol);
            scopeInsertParametersOf(symbol);
            pushMember(symbol);
            if (!symbol.isDeferred()) expression(rhs, symbol.resultType());
            popMember();
            scopeRemoveParametersOf(symbol);
            return true;

        default:
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Checking statements

    /** Checks the statement. Returns true. */
    private boolean statement(Set locals, Tree tree) {
        switch (tree) {

        case Empty:
            return true;

        case ValDef(_, _, _, Tree rhs):
            Symbol symbol = tree.symbol();
            assert symbol != null && symbol.isTerm(): show(tree);
            scopeInsertVVariable(symbol, false);
            locals.add(symbol);
            pushOwner(symbol);
            expression(rhs, symbol.type());
            popOwner();
            return true;

        default:
            return expression(tree, tree.type());
        }
    }

    //########################################################################
    // Private Methods - Checking expressions

    /** Checks the expression. Returns true. */
    private boolean expression(Tree tree, Type expected) {
        return true;
    }

    /** Checks the type application. Returns true. */
    private boolean tapply(Tree tree, Type function, Tree[] args) {
        Symbol[] params = function.typeParams();
        assert params.length == args.length: show(tree)
            + format("params", Debug.show(params));
        Type.Map subst = Type.getSubst(params, Tree.typeOf(args));
        for (int i = 0; i < args.length; i++) {
            Type expected = subst.apply(params[i].info());
            Type loBound = subst.apply(params[i].loBound());
            type(args[i], expected, loBound);
        }
        type(tree, subst.apply(function.resultType()));
        return true;
    }

    /** Checks the value application. Returns true. */
    private boolean vapply(Tree tree, Type function, Tree[] args) {
        Symbol[] params = function.valueParams();
        assert params.length == args.length: show(tree)
            + format("params", Debug.show(params));
        for (int i = 0; i < args.length; i++)
            expression(args[i], params[i].type());
        type(tree, function.resultType());
        return true;
    }

    //########################################################################
    // Private Methods - Checking functions

    /** Checks the function. Returns true. */
    private boolean function(Tree tree) {
        Symbol symbol = tree.symbol();
        assert symbol != null && symbol.isTerm(): show(tree);
        assert symbol.isMethod(): show(tree);
        switch (tree) {

        case Select(Tree qualifier, _):
            return selection(tree);

        case Ident(_):
            if (symbol.isInitializer()) return true;
            assert labels.contains(symbol): show(tree);
            assert symbol.owner() == currentMember(): show(tree);
            return true;


        default:
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Checking locations

    /** Checks the location. Returns true. */
    public boolean location(Tree tree) {
        switch (tree) {

        case Select(_, _):
            Symbol symbol = tree.symbol();
            assert symbol != null && symbol.isTerm(): show(tree);
            assert !symbol.isMethod(): show(tree);
            return selection(tree);

        case Ident(_):
            Symbol symbol = tree.symbol();
            assert symbol != null && symbol.isTerm(): show(tree);
            if (symbol.isModule()) return true;
            assert vvars.contains(symbol): show(tree);
            return true;

        default:
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Checking selection

    /** Checks the selection. Returns true. */
    public boolean selection(Tree tree) {
        switch (tree) {

        case Select(Tree qualifier, _):
            Symbol symbol = tree.symbol();
            assert symbol != null && symbol.isTerm(): show(tree);
            Symbol owner = symbol.owner();
            assert owner.isClassType(): show(tree);
            Type prefix = qualifier.type();
            assert prefix.baseType(owner) != Type.NoType: show(tree);
            return expression(qualifier, prefix);

        default:
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Checking type

    private boolean type(Tree tree, Type expected, Type loBound) {
        if (!loBound.isSubType(tree.type())) {
            Type.explainSwitch = true;
            assert loBound.isSubType(tree.type()): show(tree)
                + format("loBound", expected);
            Type.explainSwitch = false;
        }
        return type(tree, expected);
    }

    private boolean type(Tree tree, Type expected) {
        if (!tree.type().isSubType(expected)) {
            Type.explainSwitch = true;
            assert tree.type().isSubType(expected): show(tree)
                + format("expected", expected);
            Type.explainSwitch = false;
        }
        return true;
    }

    //########################################################################
    // Private Methods - Declaring symbols

    /** Do the trees contain the given symbols? */
    private boolean containSymbols(Tree[] trees, Symbol[] symbols) {
        if (trees.length != symbols.length) return false;
        for (int i = 0; i < trees.length; i++)
            if (trees[i].symbol() != symbols[i]) return false;
        return true;
    }

    /** Remove parameters of symbol from current scope. */
    private void scopeInsertParametersOf(Symbol symbol) {
        Symbol[] tparams = symbol.typeParams();
        for (int i = 0; i < tparams.length; i++)
            scopeInsertTVariable(tparams[i], true);
        Symbol[] vparams = symbol.valueParams();
        for (int i = 0; i < vparams.length; i++)
            scopeInsertVVariable(vparams[i], true);
    }

    /** Adds the type variable to the current scope. */
    private void scopeInsertTVariable(Symbol symbol, boolean isParameter) {
        assert !symbol.owner().isClass(): show(symbol);
        assert symbol.isParameter() == isParameter: show(symbol);
        registerSymbol(symbol);
        tvars.add(symbol);
    }

    /** Adds the value variable to the current scope. */
    private void scopeInsertVVariable(Symbol symbol, boolean isParameter) {
        assert !symbol.owner().isClass(): show(symbol);
        assert symbol.isParameter() == isParameter: show(symbol);
        registerSymbol(symbol);
        vvars.add(symbol);
    }

    /** Adds the label to current scope. */
    private void scopeInsertLabel(Symbol symbol) {
        assert !symbol.owner().isClass(): show(symbol);
        registerSymbol(symbol);
        labels.add(symbol);
    }

    /** Remove parameters of symbol from current scope. */
    private void scopeRemoveParametersOf(Symbol symbol) {
        Symbol[] tparams = symbol.typeParams();
        for (int i = 0; i < tparams.length; i++)
            scopeRemoveTVariable(tparams[i]);
        Symbol[] vparams = symbol.valueParams();
        for (int i = 0; i < vparams.length; i++)
            scopeRemoveVVariable(vparams[i]);
    }

    /** Removes the type variable from current scope. */
    private void scopeRemoveTVariable(Symbol symbol) {
        boolean removed = tvars.remove(symbol);
        assert removed: show(symbol);
    }

    /** Removes the value variable from current scope. */
    private void scopeRemoveVVariable(Symbol symbol) {
        boolean removed = vvars.remove(symbol);
        assert removed: show(symbol);
    }

    /** Removes the label symbol from current scope. */
    private void scopeRemoveLabel(Symbol symbol) {
        boolean removed = labels.remove(symbol);
        assert removed: show(symbol);
    }

    /** Registers the symbol. */
    private void registerSymbol(Symbol symbol) {
        boolean added = symbols.add(symbol);
        assert added: show(symbol);
    }

    //########################################################################
    // Private Methods - Managing current context

    /** Returns the current unit. */
    private Unit currentUnit() {
        return units.size() > 0 ? (Unit)units.peek() : null;
    }

    /** Returns the current class. */
    private Symbol currentClass() {
        return classes.size() > 0 ? (Symbol)classes.peek() : null;
    }

    /** Returns the current member. */
    private Symbol currentMember() {
        return members.size() > 0 ? (Symbol)members.peek() : null;
    }

    /** Returns the current owner. */
    private Symbol currentOwner() {
        return owners.size() > 0 ? (Symbol)owners.peek() : null;
    }

    /** Sets the current unit to the given one. */
    private void pushUnit(Unit unit) {
        assert units.size() == 0: showPush(unit);
        assert classes.size() == 0: showPush(unit);
        assert members.size() == 0: showPush(unit);
        units.push(unit);
    }

    /** Sets the current class to the given one. */
    private void pushClass(Symbol clasz) {
        assert clasz.isClass(): showPush(clasz);
        assert members.size() == 0: showPush(clasz);
        classes.push(clasz);
        pushOwner(clasz);
    }

    /** Sets the current member to the given one. */
    private void pushMember(Symbol member) {
        assert member.isTerm(): showPush(member);
        assert members.size() == 0: showPush(member);
        assert member.owner() == currentClass(): showPush(member);
        members.push(member);
        pushOwner(member);
    }

    /** Sets the current owner to the given one. */
    private void pushOwner(Symbol owner) {
        owners.push(owner);
    }

    /** Sets the current unit to the previous one. */
    private void popUnit() {
        assert units.size() > 0: show();
        units.pop();
    }

    /** Sets the current class to the previous one. */
    private void popClass() {
        assert members.size() == 0: show();
        assert classes.size() > 0: show();
        popOwner();
        classes.pop();
    }

    /** Sets the current member to the previous one. */
    private void popMember() {
        assert members.size() > 0: show();
        popOwner();
        members.pop();
    }

    /** Sets the current owner to the previous one. */
    private void popOwner() {
        assert owners.size() > 0: show();
        owners.pop();
    }

    //########################################################################
    // Private Methods - Showing errors

    /** Returns the current position. */
    private String show() {
        return format("unit", currentUnit())
            + format("clasz", currentClass())
            + format("member", currentMember())
            + format("owner", currentOwner());
    }

    /** Returns the current position and given header and value. */
    private String show(String header, Object value) {
        return show() + format(header, value);
    }

    /** Returns the current position and given symbol. */
    private String show(Symbol symbol) {
        return show("symbol", symbol)
            + format("symbol.pos", Position.toString(symbol.pos))
            + format("symbol.info", symbol.info());
    }

    /** Returns the current position and given tree. */
    private String show(Tree tree) {
        return show("tree", tree)
            + format("tree.pos", Position.toString(tree.pos))
            + format("tree.symbol", tree.symbol())
            + format("tree.type", tree.type());
    }

    /** Returns the current position and given pushed value. */
    private String showPush(Object value) {
        return show("pushing", value);
    }

    /** Returns a string with the given header and value. */
    private String format(String header, Object value) {
        while (header.length() < 12) header = header + ' ';
        return "\n" + header + ": " + toString(value);
    }

    /** Returns a string representation of the given value. */
    private String toString(Object value) {
        if (value instanceof Symbol) return Debug.show(value);
        return String.valueOf(value);
    }

    //########################################################################
}
