package io.github.sefiraat.equivalencytech.compat;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprueba que todo lo que el bytecode del plugin usa de la API de Paper existe en la API que hay
 * en el classpath de pruebas: clases, metodos, constructores y campos, con su descriptor exacto
 * (nombre, parametros y tipo de retorno), resueltos como lo haria la JVM al enlazar.
 *
 * <p>Compilar contra una API solo demuestra compatibilidad de codigo fuente. Si Paper cambia el tipo
 * de retorno de un metodo, el codigo sigue compilando, pero el jar ya publicado (compilado contra
 * 1.21.11) falla en el servidor con NoSuchMethodError. Por eso este test se ejecuta de dos formas:</p>
 * <ul>
 *   <li>{@code mvn -Ppaper-26.2 test}: plugin compilado y probado contra 26.2.</li>
 *   <li>{@code mvn test-compile && mvn -Ppaper-26.2 surefire:test}: bytecode compilado contra
 *       1.21.11, es decir, el del jar publicado, enlazado contra la API 26.2.</li>
 * </ul>
 */
class PaperApiLinkageTest {

    /** Paquetes que forman la API del servidor. Slimefun y las dependencias sombreadas no entran. */
    private static final List<String> API_PACKAGES = List.of(
        "org/bukkit/", "io/papermc/", "com/destroystokyo/", "net/kyori/", "net/md_5/"
    );

    private enum Kind { TYPE, METHOD, FIELD }

    private record Ref(Kind kind, String owner, String name, String descriptor) {
        @Override
        public String toString() {
            String owner = this.owner.replace('/', '.');
            return switch (kind) {
                case TYPE -> "clase " + owner;
                case METHOD -> "metodo " + owner + "#" + name + descriptor;
                case FIELD -> "campo " + owner + "#" + name + " : " + descriptor;
            };
        }
    }

    @Test
    void laApiDelClasspathEsLaDelPerfil() {
        // Sin esta guarda, un perfil mal escrito dejaria correr el resto contra 1.21.11 en silencio.
        String expected = System.getProperty("paper.version");
        assumeTrue(expected != null && !expected.isBlank() && !expected.startsWith("${"),
            "paper.version llega desde la configuracion de surefire en el pom");
        String jar = Bukkit.class.getProtectionDomain().getCodeSource().getLocation().toString();
        assertTrue(jar.contains(expected), "Se esperaba paper-api " + expected + " y el classpath trae " + jar);
    }

    @Test
    void todasLasReferenciasALaApiExistenEnLaVersionDelClasspath() throws IOException, URISyntaxException {
        Path classes = Path.of(EquivalencyTech.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Map<Ref, Set<String>> refs = collectApiReferences(classes);

        // Si el escaneo no encuentra nada, el test pasaria sin comprobar nada.
        assertTrue(refs.size() > 100, "Solo se encontraron " + refs.size() + " referencias a la API en " + classes);

        List<String> problems = new ArrayList<>();
        ClassLoader loader = getClass().getClassLoader();
        for (Map.Entry<Ref, Set<String>> entry : refs.entrySet()) {
            String problem = check(entry.getKey(), loader);
            if (problem != null) {
                problems.add("- " + entry.getKey() + ": " + problem + " (usado en " + String.join(", ", entry.getValue()) + ")");
            }
        }
        String api = Bukkit.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        assertTrue(problems.isEmpty(), problems.size() + " referencia(s) a la API no existen en "
            + api + ":\n" + String.join("\n", problems));
    }

    @Test
    void elResolutorDetectaReferenciasRotas() {
        // Autoprueba: si el resolutor aceptase cualquier cosa, el test de enlace pasaria siempre.
        ClassLoader loader = getClass().getClassLoader();
        String itemStack = "org/bukkit/inventory/ItemStack";

        assertNull(check(new Ref(Kind.METHOD, itemStack, "getPersistentDataContainer",
            "()Lio/papermc/paper/persistence/PersistentDataContainerView;"), loader));
        // Mismo nombre y parametros, otro tipo de retorno: es el caso que compila pero rompe el jar.
        assertNotNull(check(new Ref(Kind.METHOD, itemStack, "getPersistentDataContainer",
            "()Lorg/bukkit/persistence/PersistentDataContainer;"), loader));
        assertNotNull(check(new Ref(Kind.METHOD, itemStack, "metodoQueNoExiste", "()V"), loader));
        assertNotNull(check(new Ref(Kind.TYPE, "org/bukkit/ClaseQueNoExiste", "", ""), loader));
        assertNotNull(check(new Ref(Kind.FIELD, "org/bukkit/Material", "CHEST", "Ljava/lang/String;"), loader));

        // Herencia: Player declara getUniqueId en Entity; equals llega desde Object.
        assertNull(check(new Ref(Kind.METHOD, "org/bukkit/entity/Player", "getUniqueId", "()Ljava/util/UUID;"), loader));
        assertNull(check(new Ref(Kind.METHOD, "org/bukkit/entity/Player", "equals", "(Ljava/lang/Object;)Z"), loader));
        assertNull(check(new Ref(Kind.FIELD, "org/bukkit/Material", "CHEST", "Lorg/bukkit/Material;"), loader));
    }

    // region Escaneo del bytecode

    private static Map<Ref, Set<String>> collectApiReferences(Path classes) throws IOException {
        Map<Ref, Set<String>> refs = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        List<Path> files;
        try (Stream<Path> walk = Files.walk(classes)) {
            files = walk.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        }
        for (Path file : files) {
            ClassReader reader = new ClassReader(Files.readAllBytes(file));
            String user = reader.getClassName().replace('/', '.');
            reader.accept(new Collector(refs, user), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return refs;
    }

    private static final class Collector extends ClassVisitor {

        private final Map<Ref, Set<String>> refs;
        private final String user;

        Collector(Map<Ref, Set<String>> refs, String user) {
            super(Opcodes.ASM9);
            this.refs = refs;
            this.user = user;
        }

        private void type(String internalName) {
            if (internalName == null) {
                return;
            }
            if (internalName.startsWith("[")) {
                descriptorTypes(internalName);
                return;
            }
            add(new Ref(Kind.TYPE, internalName, "", ""));
        }

        private void descriptorTypes(String descriptor) {
            Type type = Type.getType(descriptor);
            if (type.getSort() == Type.METHOD) {
                for (Type argument : type.getArgumentTypes()) {
                    descriptorTypes(argument.getDescriptor());
                }
                descriptorTypes(type.getReturnType().getDescriptor());
            } else if (type.getSort() == Type.ARRAY) {
                descriptorTypes(type.getElementType().getDescriptor());
            } else if (type.getSort() == Type.OBJECT) {
                type(type.getInternalName());
            }
        }

        private void member(Kind kind, String owner, String name, String descriptor) {
            descriptorTypes(descriptor);
            if (owner.startsWith("[")) {
                // Metodos de arrays (clone): pertenecen a Object, solo cuenta el tipo del elemento.
                descriptorTypes(owner);
                return;
            }
            type(owner);
            add(new Ref(kind, owner, name, descriptor));
        }

        private void handle(Handle handle) {
            Kind kind = handle.getTag() <= Opcodes.H_PUTSTATIC ? Kind.FIELD : Kind.METHOD;
            member(kind, handle.getOwner(), handle.getName(), handle.getDesc());
        }

        private void constant(Object value) {
            if (value instanceof Type type) {
                descriptorTypes(type.getDescriptor());
            } else if (value instanceof Handle handle) {
                handle(handle);
            }
        }

        private void add(Ref ref) {
            if (API_PACKAGES.stream().anyMatch(ref.owner()::startsWith)) {
                refs.computeIfAbsent(ref, r -> new LinkedHashSet<>()).add(user);
            }
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            type(superName);
            if (interfaces != null) {
                for (String itf : interfaces) {
                    type(itf);
                }
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            descriptorTypes(descriptor);
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            descriptorTypes(descriptor);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            descriptorTypes(descriptor);
            if (exceptions != null) {
                for (String exception : exceptions) {
                    type(exception);
                }
            }
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    // Sin la clase de @EventHandler, Bukkit no registraria el listener.
                    descriptorTypes(annotation);
                    return null;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                    member(Kind.METHOD, owner, method, desc);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                    member(Kind.FIELD, owner, field, desc);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    type(type);
                }

                @Override
                public void visitLdcInsn(Object value) {
                    constant(value);
                }

                @Override
                public void visitInvokeDynamicInsn(String method, String desc, Handle bootstrap, Object... arguments) {
                    descriptorTypes(desc);
                    for (Object argument : arguments) {
                        constant(argument);
                    }
                }

                @Override
                public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                                               org.objectweb.asm.Label handler, String type) {
                    type(type);
                }

                @Override
                public void visitMultiANewArrayInsn(String desc, int dimensions) {
                    descriptorTypes(desc);
                }
            };
        }
    }

    // endregion

    // region Resolucion como la JVM

    private static String check(Ref ref, ClassLoader loader) {
        Class<?> owner;
        try {
            owner = Class.forName(ref.owner().replace('/', '.'), false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return "la clase no existe (" + e.getClass().getSimpleName() + ")";
        }
        try {
            return switch (ref.kind()) {
                case TYPE -> null;
                case METHOD -> "<init>".equals(ref.name())
                    ? (hasConstructor(owner, ref.descriptor()) ? null : "no existe ese constructor")
                    : (resolveMethod(owner, ref.name(), ref.descriptor()) ? null : "no existe ese metodo");
                case FIELD -> resolveField(owner, ref.name(), ref.descriptor()) ? null : "no existe ese campo";
            };
        } catch (LinkageError e) {
            return "no se pudo inspeccionar la clase (" + e + ")";
        }
    }

    private static boolean hasConstructor(Class<?> owner, String descriptor) {
        for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
            if (Type.getConstructorDescriptor(constructor).equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    /** Clase, superclases y despues todas las superinterfaces; Object cubre equals/hashCode en interfaces. */
    private static boolean resolveMethod(Class<?> owner, String name, String descriptor) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            if (declaresMethod(c, name, descriptor)) {
                return true;
            }
            collectInterfaces(c, interfaces);
        }
        for (Class<?> itf : interfaces) {
            if (declaresMethod(itf, name, descriptor)) {
                return true;
            }
        }
        return owner.isInterface() && declaresMethod(Object.class, name, descriptor);
    }

    private static boolean declaresMethod(Class<?> type, String name, String descriptor) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean resolveField(Class<?> owner, String name, String descriptor) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getName().equals(name) && Type.getDescriptor(field.getType()).equals(descriptor)) {
                    return true;
                }
            }
            collectInterfaces(c, interfaces);
        }
        for (Class<?> itf : interfaces) {
            for (Field field : itf.getDeclaredFields()) {
                if (field.getName().equals(name) && Type.getDescriptor(field.getType()).equals(descriptor)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> into) {
        for (Class<?> itf : type.getInterfaces()) {
            if (into.add(itf)) {
                collectInterfaces(itf, into);
            }
        }
    }

    // endregion
}
