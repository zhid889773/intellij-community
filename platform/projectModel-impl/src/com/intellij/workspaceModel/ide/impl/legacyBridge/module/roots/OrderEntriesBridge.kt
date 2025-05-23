// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.impl.ClonableOrderEntry
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.entities.DependencyScope as EntitiesDependencyScope
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import java.util.*

internal abstract class OrderEntryBridge(
  private val rootModel: ModuleRootModelBridge,
  private val initialIndex: Int,
  var item: ModuleDependencyItem,
  private val itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntry {
  protected var index = initialIndex

  protected val ownerModuleBridge: ModuleBridge
    get() = rootModel.moduleBridge

  protected val updater: (Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit
    get() = itemUpdater ?: error("This mode is read-only. Call from a modifiable model")

  fun getRootModel(): ModuleRootModelBridge = rootModel

  fun updateIndex(newIndex: Int) {
    index = newIndex
  }

  internal val currentIndex: Int
    get() = index

  override fun getOwnerModule() = ownerModuleBridge
  override fun compareTo(other: OrderEntry?) = index.compareTo((other as OrderEntryBridge).index)
  override fun isValid() = true
  override fun isSynthetic() = false

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as OrderEntryBridge

    if (ownerModuleBridge != other.ownerModuleBridge) return false
    if (initialIndex != other.initialIndex) return false
    if (item != other.item) return false

    return true
  }

  override fun hashCode(): Int {
    var result = ownerModuleBridge.hashCode()
    result = 31 * result + initialIndex
    result = 31 * result + item.hashCode()
    return result
  }

  override fun toString(): String = "${shortClassName}: $presentableName"

  protected val shortClassName: String 
    get() = javaClass.name.substringAfterLast(".").removeSuffix("Bridge")
}

internal class ModuleOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  moduleDependencyItem: ModuleDependency,
  itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntryBridge(rootModel, index, moduleDependencyItem, itemUpdater), ExportableOrderEntry, ModuleOrderEntry, ClonableOrderEntry {

  private val moduleDependencyItem
    get() = item as ModuleDependency

  override fun isExported() = moduleDependencyItem.exported

  override fun setExported(value: Boolean) {
    if (isExported == value) return
    updater(index) { (it as ModuleDependency).copy(exported = value) }
    item = (item as ModuleDependency).copy(exported = value)
  }

  override fun getScope() = moduleDependencyItem.scope.toDependencyScope()

  override fun setScope(scope: DependencyScope) {
    if (getScope() == scope) return
    updater(index) { (it as ModuleDependency).copy(scope = scope.toEntityDependencyScope()) }
    item = (item as ModuleDependency).copy(scope = scope.toEntityDependencyScope())
  }

  override fun getModule(): Module? {
    val storage = getRootModel().storage
    val moduleEntity = storage.resolve(moduleDependencyItem.module)
    val module = moduleEntity?.findModule(storage)
    return getRootModel().accessor.getModule(module, moduleName)
  }

  override fun getModuleName() = moduleDependencyItem.module.name

  override fun isProductionOnTestDependency() = moduleDependencyItem.productionOnTest

  override fun setProductionOnTestDependency(productionOnTestDependency: Boolean) {
    if (isProductionOnTestDependency == productionOnTestDependency) return
    updater(index) { item ->
      (item as ModuleDependency).copy(productionOnTest = productionOnTestDependency)
    }
    item = (item as ModuleDependency).copy(productionOnTest = productionOnTestDependency)
  }

  override fun getFiles(type: OrderRootType): Array<VirtualFile> = getEnumerator(type)?.roots ?: VirtualFile.EMPTY_ARRAY

  private fun getEnumerator(rootType: OrderRootType) = ownerModuleBridge.let {
    getEnumeratorForType(rootType, it).usingCache()
  }

  private fun getEnumeratorForType(type: OrderRootType, module: Module): OrderRootsEnumerator {
    val base = OrderEnumerator.orderEntries(module)
    if (type === OrderRootType.CLASSES) {
      return base.exportedOnly().withoutModuleSourceEntries().recursively().classes()
    }
    return if (type === OrderRootType.SOURCES) {
      base.exportedOnly().recursively().sources()
    }
    else base.roots(type)
  }

  override fun getPresentableName() = moduleName

  override fun isValid(): Boolean = module != null

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitModuleOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = ModuleOrderEntryBridge(
    rootModel as ModuleRootModelBridge,
    index, moduleDependencyItem.copy(), null
  )
}

internal fun EntitiesDependencyScope.toDependencyScope(): DependencyScope = when (this) {
  EntitiesDependencyScope.COMPILE -> DependencyScope.COMPILE
  EntitiesDependencyScope.RUNTIME -> DependencyScope.RUNTIME
  EntitiesDependencyScope.PROVIDED -> DependencyScope.PROVIDED
  EntitiesDependencyScope.TEST -> DependencyScope.TEST
}

internal fun DependencyScope.toEntityDependencyScope(): EntitiesDependencyScope = when (this) {
  DependencyScope.COMPILE -> EntitiesDependencyScope.COMPILE
  DependencyScope.RUNTIME -> EntitiesDependencyScope.RUNTIME
  DependencyScope.PROVIDED -> EntitiesDependencyScope.PROVIDED
  DependencyScope.TEST -> EntitiesDependencyScope.TEST
}

internal abstract class SdkOrderEntryBaseBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  item: ModuleDependencyItem
) : OrderEntryBridge(rootModel, index, item, null), LibraryOrSdkOrderEntry {

  protected abstract val rootProvider: RootProvider?

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY
}

internal class LibraryOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  libraryDependencyItem: LibraryDependency,
  itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntryBridge(rootModel, index, libraryDependencyItem, itemUpdater), ExportableOrderEntry, LibraryOrderEntry, ClonableOrderEntry {

  internal val libraryDependencyItem
    get() = item as LibraryDependency

  override fun isExported() = libraryDependencyItem.exported

  override fun setExported(value: Boolean) {
    if (isExported == value) return
    updater(index) { (it as LibraryDependency).copy(exported = value) }
    item = (item as LibraryDependency).copy(exported = value)
  }

  override fun getScope() = libraryDependencyItem.scope.toDependencyScope()

  override fun setScope(scope: DependencyScope) {
    if (getScope() == scope) return
    updater(index) { (it as LibraryDependency).copy(scope = scope.toEntityDependencyScope()) }
    item = (item as LibraryDependency).copy(scope = scope.toEntityDependencyScope())
  }

  override fun getPresentableName(): String = libraryName ?: getPresentableNameForUnnamedLibrary()

  @Nls
  private fun getPresentableNameForUnnamedLibrary(): String {
    val url = getRootUrls(OrderRootType.CLASSES).firstOrNull()
    return if (url != null) PathUtil.toPresentableUrl(url) else ProjectModelBundle.message("empty.library.title")
  }

  private val rootProvider: RootProvider?
    get() = library?.rootProvider

  override fun getLibraryLevel() = libraryDependencyItem.library.tableId.level

  override fun getLibraryName() = LibraryNameGenerator.getLegacyLibraryName(libraryDependencyItem.library)

  override fun getLibrary(): Library? {
    val libraryId = libraryDependencyItem.library
    val tableId = libraryId.tableId
    val library = if (tableId is LibraryTableId.GlobalLibraryTableId) {
      LibraryTablesRegistrar.getInstance()
        .getLibraryTableByLevel(tableId.level, ownerModuleBridge.project)
        ?.getLibraryByName(libraryId.name)
    }
    else {
      val storage = getRootModel().storage
      val libraryEntity = storage.resolve(libraryId)
      libraryEntity?.let { storage.libraryMap.getDataByEntity(libraryEntity) }
    }

    return if (tableId is LibraryTableId.ModuleLibraryTableId) {
      // model.accessor.getLibrary is not applicable to module libraries
      library
    }
    else {
      getRootModel().accessor.getLibrary(library, libraryName, libraryLevel)
    }
  }

  override fun isModuleLevel() = libraryLevel == JpsLibraryTableSerializer.MODULE_LEVEL

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun isValid(): Boolean = library != null

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitLibraryOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry {
    return LibraryOrderEntryBridge(getRootModel(), index, libraryDependencyItem, null)
  }

  override fun isSynthetic(): Boolean = isModuleLevel

  override fun toString(): String {
    return buildString {
      append(shortClassName)
      append(": ")
      append(ownerModule.name)
      append(" -> ")
      append(presentableName)
      if (libraryDependencyItem.scope != EntitiesDependencyScope.COMPILE) {
        append(", scope=")
        append(libraryDependencyItem.scope.name.lowercase(Locale.US))
      }
      if (libraryDependencyItem.exported) {
        append(", exported")
      }
    }
  }
}

internal class SdkOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  internal val sdkDependencyItem: SdkDependency
) : SdkOrderEntryBaseBridge(rootModel, index, sdkDependencyItem), ModuleJdkOrderEntry, ClonableOrderEntry {

  override val rootProvider: RootProvider?
    get() = jdk?.rootProvider

  override fun getPresentableName() = "< ${jdk?.name ?: sdkDependencyItem.sdk.name} >"

  override fun isValid(): Boolean = jdk != null

  override fun getJdk(): Sdk? {
    val sdkType = sdkDependencyItem.sdk.type
    val sdkName = sdkDependencyItem.sdk.name
    val sdk = ModifiableRootModelBridge.findSdk(sdkName, sdkType)
    return getRootModel().accessor.getSdk(sdk, sdkName)
  }

  override fun getJdkName() = sdkDependencyItem.sdk.name

  override fun getJdkTypeName() = sdkDependencyItem.sdk.type

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitJdkOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = SdkOrderEntryBridge(rootModel as ModuleRootModelBridge, index, sdkDependencyItem.copy())

  override fun isSynthetic(): Boolean = true
}

internal class InheritedSdkOrderEntryBridge(rootModel: ModuleRootModelBridge, index: Int, item: InheritedSdkDependency)
  : SdkOrderEntryBaseBridge(rootModel, index, item), InheritedJdkOrderEntry, ClonableOrderEntry {

  override val rootProvider: RootProvider?
    get() = jdk?.rootProvider

  override fun getJdk(): Sdk? = getRootModel().accessor.getProjectSdk(getRootModel().moduleBridge.project)
  override fun getJdkName(): String? = getRootModel().accessor.getProjectSdkName(getRootModel().moduleBridge.project)

  override fun isValid(): Boolean = jdk != null

  override fun getPresentableName() = "< $jdkName >"

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitInheritedJdkOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = InheritedSdkOrderEntryBridge(
    rootModel as ModuleRootModelBridge, index, InheritedSdkDependency
  )
}

internal class ModuleSourceOrderEntryBridge(rootModel: ModuleRootModelBridge, index: Int, item: ModuleSourceDependency)
  : OrderEntryBridge(rootModel, index, item, null), ModuleSourceOrderEntry, ClonableOrderEntry {
  override fun getFiles(type: OrderRootType): Array<out VirtualFile> = if (type == OrderRootType.SOURCES) rootModel.sourceRoots else VirtualFile.EMPTY_ARRAY

  override fun getPresentableName(): String = ProjectModelBundle.message("project.root.module.source")

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? =
    policy.visitModuleSourceOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = ModuleSourceOrderEntryBridge(
    rootModel as ModuleRootModelBridge, index, ModuleSourceDependency
  )

  override fun isSynthetic(): Boolean = true
}
