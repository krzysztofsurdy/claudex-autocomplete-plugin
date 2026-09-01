package dev.ksurdy.claudeautocomplete.php

import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType

object PhpClassOutline {
    fun render(phpClass: PhpClass): String {
        val out = StringBuilder()
        phpClass.namespaceName.trim('\\').takeIf { it.isNotEmpty() }?.let { out.append("namespace ").append(it).append(";\n\n") }
        out.append(header(phpClass)).append("\n{\n")
        phpClass.traits.forEach { out.append("    use ").append(it.name).append(";\n") }
        val fields = phpClass.ownFields.filter { isVisible(it.modifier.isPrivate) }
        fields.filter { it.isConstant }.forEach { out.append("    ").append(constant(it)).append("\n") }
        fields.filter { !it.isConstant }.forEach { out.append("    ").append(property(it)).append("\n") }
        phpClass.ownMethods.filter { isVisible(it.access.isPrivate) }.forEach { out.append("    ").append(method(it)).append("\n") }
        return out.append("}").toString()
    }

    private fun isVisible(isPrivate: Boolean) = !isPrivate

    private fun header(phpClass: PhpClass): String {
        val kind = when {
            phpClass.isInterface -> "interface"
            phpClass.isTrait -> "trait"
            phpClass.isEnum -> "enum"
            else -> "class"
        }
        val modifiers = buildList {
            if (phpClass.isAbstract && kind == "class") add("abstract")
            if (phpClass.isFinal && kind == "class") add("final")
            if (phpClass.isReadonly) add("readonly")
        }
        val parts = ArrayList<String>(modifiers)
        parts += kind
        parts += phpClass.name
        phpClass.superFQN?.takeIf { it.isNotEmpty() && !phpClass.isInterface }?.let { parts += "extends ${shortName(it)}" }
        val interfaces = phpClass.directImplementedInterfaces.map { it.name }
        if (interfaces.isNotEmpty()) parts += (if (phpClass.isInterface) "extends " else "implements ") + interfaces.joinToString(", ")
        return parts.joinToString(" ")
    }

    private fun constant(field: Field): String {
        val value = field.defaultValuePresentation?.takeIf { it.isNotEmpty() }?.let { " = $it" }.orEmpty()
        return "${access(field.modifier.isProtected)}const ${field.name}$value;"
    }

    private fun property(field: Field): String {
        val parts = ArrayList<String>()
        parts += if (field.modifier.isProtected) "protected" else "public"
        if (field.modifier.isStatic) parts += "static"
        if (field.isReadonly) parts += "readonly"
        typeText(field.declaredType)?.let { parts += it }
        parts += "$" + field.name.removePrefix("$")
        return parts.joinToString(" ") + ";"
    }

    private fun method(method: Method): String {
        val parts = ArrayList<String>()
        parts += if (method.access.isProtected) "protected" else "public"
        if (method.isStatic) parts += "static"
        if (method.isAbstract && !method.containingClass!!.isInterface) parts += "abstract"
        if (method.isFinal) parts += "final"
        val params = method.parameters.joinToString(", ") { param(it as Parameter) }
        val returns = typeText(method.declaredType)?.let { ": $it" }.orEmpty()
        return parts.joinToString(" ") + " function ${method.name}($params)$returns;"
    }

    private fun param(parameter: Parameter): String {
        val type = typeText(parameter.declaredType)?.plus(" ").orEmpty()
        val byRef = if (parameter.isPassByRef) "&" else ""
        val variadic = if (parameter.isVariadic) "..." else ""
        val default = parameter.defaultValuePresentation?.takeIf { it.isNotEmpty() }?.let { " = $it" }.orEmpty()
        return "$type$byRef$variadic\$${parameter.name}$default"
    }

    private fun access(isProtected: Boolean) = if (isProtected) "protected " else ""

    fun typeText(type: PhpType): String? {
        if (type.isEmpty) return null
        return type.types.filter { it.isNotEmpty() }.joinToString("|") { shortName(it) }.takeIf { it.isNotEmpty() }
    }

    private fun shortName(name: String): String = name.trimStart('\\').substringAfterLast('\\')
}
