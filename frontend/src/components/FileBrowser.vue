<template>
    <div class="file-browser">
      <div class="browser-container">
        <!-- 左侧目录树 -->
        <div class="directory-tree">
          <el-tree
            :data="directoryTree"
            :props="defaultProps"
            @node-click="handleNodeClick"
            @node-expand="handleNodeExpand"
            :highlight-current="true"
            node-key="path"
            :expand-on-click-node="false"
            :default-expanded-keys="['/']"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node">
                <el-icon><folder /></el-icon>
                <span>{{ node.label }}</span>
              </span>
            </template>
          </el-tree>
        </div>
  
        <!-- 右侧文件列表 -->
        <div class="file-list">
          <div class="file-list-header">
            <span class="current-path">当前路径：{{ currentPath }}</span>
          </div>
          <el-table 
            :data="fileList" 
            style="width: 100%"
            v-loading="loading"
          >
            <el-table-column prop="name" label="文件名" min-width="200">
              <template #default="{ row }">
                <div class="file-name-cell">
                  <el-icon><document /></el-icon>
                  <span style="margin-left: 8px">{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="size" label="大小" width="120">
              <template #default="{ row }">
                {{ formatFileSize(row.size) }}
              </template>
            </el-table-column>
            <el-table-column prop="lastModified" label="修改时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.lastModified) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  @click="handlePreview(row)"
                >
                  预览
                </el-button>
                <el-button
                  type="primary"
                  link
                  @click="handleDownload(row)"
                >
                  下载
                </el-button>
                <el-button
                  type="danger"
                  link
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
  
      <!-- 文件预览对话框 -->
      <el-dialog
        v-model="previewVisible"
        :title="previewFile?.name"
        width="80%"
        class="preview-dialog"
        destroy-on-close
      >
        <div class="preview-content">
          <!-- 图片预览 -->
          <img
            v-if="isImage"
            :src="previewUrl"
            class="preview-image"
            alt="预览图片"
          />
          <!-- PDF预览 -->
          <iframe
            v-else-if="isPdf"
            :src="previewUrl"
            class="preview-pdf"
          ></iframe>
          <!-- 文本预览 -->
          <div
            v-else-if="isText"
            class="preview-text"
          >
            {{ previewContent }}
          </div>
          <!-- 不支持预览 -->
          <div v-else class="preview-unsupported">
            该文件类型暂不支持预览
          </div>
        </div>
      </el-dialog>
    </div>
  </template>
  
  <script setup>
  import { ref, computed, onMounted } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { Folder, Document } from '@element-plus/icons-vue';
  import { fileApi } from '../api/file';
  
  const fileList = ref([]);
  const currentPath = ref('');
  const directoryTree = ref([]);
  const previewVisible = ref(false);
  const previewFile = ref(null);
  const previewUrl = ref('');
  const previewContent = ref('');
  const loading = ref(false);
  
  const defaultProps = {
    children: 'children',
    label: 'label'
  };
  
  const isImage = computed(() => {
    if (!previewFile.value) return false;
    const ext = previewFile.value.name.toLowerCase().split('.').pop();
    return ['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext);
  });
  
  const isPdf = computed(() => {
    if (!previewFile.value) return false;
    return previewFile.value.name.toLowerCase().endsWith('.pdf');
  });
  
  const isText = computed(() => {
    if (!previewFile.value) return false;
    const ext = previewFile.value.name.toLowerCase().split('.').pop();
    return ['txt', 'json', 'xml', 'html', 'css', 'js', 'md'].includes(ext);
  });
  
  const formatFileSize = (size) => {
    if (size === undefined) return '-';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let index = 0;
    while (size >= 1024 && index < units.length - 1) {
      size /= 1024;
      index++;
    }
    return `${size.toFixed(2)} ${units[index]}`;
  };
  
  const formatDate = (timestamp) => {
    if (!timestamp) return '-';
    return new Date(timestamp).toLocaleString();
  };
  
  const loadDirectory = async (path) => {
    try {
      loading.value = true;
      const response = await fileApi.listFiles(path);
      const items = response.data;
      
      // 处理目录和文件
      const dirs = items.filter(item => item.isDir)
        .map(dir => ({
          label: dir.name.replace(/\/$/, ''), // 移除末尾的斜杠
          path: path === '/' ? dir.name : `${path}${dir.name}`, // 保持完整路径
          isDirectory: true,
          children: [] // 初始化子节点数组
        }));
      
      const files = items.filter(item => !item.isDir)
        .map(file => ({
          name: file.name, // 保持原始文件名
          size: file.size, // 保持原始大小
          lastModified: file.lastModified, // 保持原始修改时间
          path: path === '/' ? file.name : `${path}${file.name}`, // 保持完整路径
          isDirectory: false
        }));
      
      // 更新目录树
      if (path === '/') {
        // 根目录
        directoryTree.value = dirs; // 只显示目录，不显示文件
      } else {
        // 查找并更新指定路径的节点
        const updateNode = (nodes, targetPath) => {
          for (let node of nodes) {
            if (node.path === targetPath) {
              node.children = dirs; // 只显示目录，不显示文件
              return true;
            }
            if (node.children && node.children.length > 0) {
              if (updateNode(node.children, targetPath)) {
                return true;
              }
            }
          }
          return false;
        };
        
        updateNode(directoryTree.value, path);
      }
      
      // 更新文件列表
      fileList.value = files;
      currentPath.value = path;
    } catch (error) {
      ElMessage.error('加载目录失败：' + error.message);
    } finally {
      loading.value = false;
    }
  };
  
  const handleNodeClick = async (data) => {
    if (data.isDirectory) {
      // 如果目录还没有加载子节点，则加载
      if (!data.children || data.children.length === 0) {
        await loadDirectory(data.path);
      }
      // 更新当前路径
      currentPath.value = data.path;
    }
  };
  
  const handleNodeExpand = async (data) => {
    if (data.isDirectory && (!data.children || data.children.length === 0)) {
      await loadDirectory(data.path);
    }
  };
  
  const handlePreview = async (file) => {
    try {
      previewFile.value = file;
      // 构建完整的文件路径
      const fullFilePath = file.path; // 直接使用完整路径
      const response = await fileApi.downloadFile(fullFilePath);
      
      if (isImage.value || isPdf.value) {
        const blob = new Blob([response.data]);
        previewUrl.value = URL.createObjectURL(blob);
      } else if (isText.value) {
        const text = await response.data.text();
        previewContent.value = text;
      }
      
      previewVisible.value = true;
    } catch (error) {
      ElMessage.error('预览失败：' + error.message);
    }
  };
  
  const handleDownload = async (file) => {
    try {
      // 构建完整的文件路径
      const fullFilePath = file.path; // 直接使用完整路径
      const response = await fileApi.downloadFile(fullFilePath);
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', file.name);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      ElMessage.error('下载失败：' + error.message);
    }
  };
  
  const handleDelete = async (file) => {
    try {
      await ElMessageBox.confirm(
        '确定要删除这个文件吗？',
        '警告',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      );
  
      // 构建完整的文件路径
      const fullFilePath = file.path; // 直接使用完整路径
      await fileApi.deleteFile(fullFilePath);
      ElMessage.success('删除成功');
      loadDirectory(currentPath.value);
    } catch (error) {
      if (error !== 'cancel') {
        ElMessage.error('删除失败：' + error.message);
      }
    }
  };
  
  onMounted(() => {
    loadDirectory('/');
  });
  </script>
  
  <style scoped>
  .file-browser {
    padding: 20px;
    height: 100%;
  }
  
  .browser-container {
    display: flex;
    gap: 20px;
    height: calc(100vh - 140px);
    background-color: #fff;
    border-radius: 4px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  }
  
  .directory-tree {
    width: 300px;
    border-right: 1px solid #dcdfe6;
    padding: 20px;
    overflow-y: auto;
  }
  
  .file-list {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
  }
  
  .file-list-header {
    margin-bottom: 16px;
    padding: 8px 0;
    border-bottom: 1px solid #dcdfe6;
  }
  
  .current-path {
    font-size: 14px;
    color: #606266;
  }
  
  .custom-tree-node {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
  }
  
  .file-name-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  
  .preview-dialog :deep(.el-dialog__body) {
    padding: 0;
  }
  
  .preview-content {
    height: 70vh;
    display: flex;
    justify-content: center;
    align-items: center;
    background-color: #f5f7fa;
  }
  
  .preview-image {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
  }
  
  .preview-pdf {
    width: 100%;
    height: 100%;
    border: none;
  }
  
  .preview-text {
    width: 100%;
    height: 100%;
    padding: 20px;
    overflow: auto;
    white-space: pre-wrap;
    font-family: monospace;
    background-color: white;
  }
  
  .preview-unsupported {
    color: #909399;
    font-size: 16px;
  }
  
  :deep(.el-tree-node__content) {
    height: 32px;
  }
  
  :deep(.el-tree-node__content:hover) {
    background-color: #f5f7fa;
  }
  
  :deep(.el-tree-node.is-current > .el-tree-node__content) {
    background-color: #ecf5ff;
  }
  
  :deep(.el-tree-node__expand-icon) {
    font-size: 16px;
  }
  
  :deep(.el-tree-node__expand-icon.expanded) {
    transform: rotate(90deg);
  }
  </style>