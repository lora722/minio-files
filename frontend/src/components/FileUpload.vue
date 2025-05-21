<template>
  <div class="upload-container">
    <div class="path-selector">
      <el-input
        v-model="currentPath"
        placeholder="请输入上传路径（例如：/dataset1/）"
        class="path-input"
      >
        <template #prepend>上传路径</template>
      </el-input>
    </div>

    <el-upload
      class="upload-component"
      drag
      :auto-upload="false"
      :on-change="handleFileChange"
      :show-file-list="true"
      :limit="1"
      accept=".zip,.tar,.7z"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">
        拖拽文件到此处或 <em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          支持 .zip、.tar、.7z 等格式的压缩文件
        </div>
      </template>
    </el-upload>

    <div v-if="uploading" class="upload-progress">
      <div class="progress-header">
        <span>正在上传：{{ currentFileName }}</span>
        <span>{{ currentFileProgress }}%</span>
      </div>
      <el-progress 
        :percentage="currentFileProgress" 
        :status="uploadStatus"
      />
      <div class="total-progress">
        <span>总进度：{{ totalProgress }}%</span>
        <el-progress 
          :percentage="totalProgress" 
          :status="uploadStatus"
        />
      </div>
    </div>

    <div class="upload-actions" v-if="selectedFile">
      <el-button type="primary" @click="startUpload" :loading="uploading">
        开始上传
      </el-button>
      <el-button @click="cancelUpload" :disabled="!uploading">
        取消上传
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import { fileApi } from '../api/file';
import JSZip from 'jszip';
import { v4 as uuidv4 } from 'uuid';

const CHUNK_SIZE = 6 * 1024 * 1024; // 6MB per chunk
const MAX_CONCURRENT_UPLOADS = 3; // 最大并发上传数

const selectedFile = ref(null);
const uploading = ref(false);
const uploadStatus = ref('');
const currentPath = ref('');
const uploadId = ref('');
const currentFileName = ref('');
const currentFileProgress = ref(0);
const totalProgress = ref(0);
const totalFiles = ref(0);
const uploadedFiles = ref(0);

const handleFileChange = (file) => {
  selectedFile.value = file.raw;
};

const extractAndUpload = async (file) => {
  try {
    const zip = new JSZip();
    const zipContent = await zip.loadAsync(file);
    
    // 获取所有文件
    const files = [];
    zip.forEach((relativePath, zipEntry) => {
      if (!zipEntry.dir) {
        files.push({
          path: relativePath,
          content: zipEntry
        });
      }
    });

    if (files.length === 0) {
      throw new Error('压缩文件中没有找到任何文件');
    }

    totalFiles.value = files.length;
    uploadedFiles.value = 0;
    totalProgress.value = 0;

    // 创建上传任务队列
    const uploadQueue = files.map(file => ({
      path: file.path,
      content: file.content
    }));

    // 并发上传文件
    while (uploadQueue.length > 0 && uploading.value) {
      const batch = uploadQueue.splice(0, MAX_CONCURRENT_UPLOADS);
      await Promise.all(batch.map(file => uploadFile(file)));
    }

    if (uploading.value) {
      ElMessage.success('所有文件上传完成');
    }
  } catch (error) {
    ElMessage.error('解压或上传失败：' + error.message);
    uploading.value = false;
  }
};

const uploadFile = async (file) => {
  try {
    currentFileName.value = file.path;
    currentFileProgress.value = 0;

    // 获取文件内容
    const content = await file.content.async('blob');
    
    // 规范化文件路径
    const normalizedPath = file.path.replace(/\\/g, '/').replace(/\/+/g, '/');
    
    // 提取文件名和目录路径
    const pathParts = normalizedPath.split('/').filter(Boolean); // 过滤空字符串
    const fileName = pathParts.pop(); // 获取文件名
    const dirPath = pathParts.join('/'); // 获取目录路径
    
    // 构建目标路径，确保路径正确
    const targetPath = [
      'uploads',
      uploadId.value,
      dirPath,
      fileName
    ].filter(Boolean).join('/');

    // 根据文件大小决定上传方式
    if (content.size <= CHUNK_SIZE) {
      // 小文件直接上传
      await fileApi.uploadFile(
        new File([content], fileName), // 只使用文件名
        targetPath
      );
      currentFileProgress.value = 100;
    } else {
      // 大文件分片上传
      const chunks = Math.ceil(content.size / CHUNK_SIZE);
      const uploadedChunks = [];

      // 分片上传
      for (let i = 0; i < chunks; i++) {
        if (!uploading.value) break;

        const start = i * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, content.size);
        const chunk = content.slice(start, end);

        // 确保分片序号从1开始
        const partNumber = i + 1;

        await fileApi.uploadChunk(
          new File([chunk], fileName), // 只使用文件名
          uploadId.value,
          partNumber,
          targetPath
        );

        uploadedChunks.push(partNumber);
        currentFileProgress.value = Math.round((uploadedChunks.length / chunks) * 100);
      }

      if (uploading.value && uploadedChunks.length > 0) {
        // 确保有分片数据才调用complete
        // 创建FormData对象
        const formData = new FormData();
        formData.append('uploadId', uploadId.value);
        formData.append('path', targetPath);
        // 添加所有分片序号
        uploadedChunks.sort((a, b) => a - b).forEach(partNumber => {
          formData.append('partNumbers', partNumber.toString());
        });

        await fileApi.completeUpload(formData);
      } else if (uploading.value) {
        throw new Error('分片上传失败');
      }
    }

    if (uploading.value) {
      uploadedFiles.value++;
      totalProgress.value = Math.round((uploadedFiles.value / totalFiles.value) * 100);
    }
  } catch (error) {
    throw new Error(`上传文件 ${file.path} 失败：${error.message}`);
  }
};

const startUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件');
    return;
  }

  if (!currentPath.value) {
    ElMessage.warning('请输入上传路径');
    return;
  }

  // 规范化上传路径
  let path = currentPath.value
    .replace(/\\/g, '/')  // 统一使用正斜杠
    .replace(/\/+/g, '/') // 移除多余的斜杠
    .replace(/^\/+|\/+$/g, ''); // 移除首尾斜杠

  // 确保路径格式正确
  path = path ? `/${path}/` : '/';

  uploading.value = true;
  uploadStatus.value = '';
  uploadId.value = uuidv4();
  currentFileProgress.value = 0;
  totalProgress.value = 0;
  uploadedFiles.value = 0;

  try {
    await extractAndUpload(selectedFile.value);
  } catch (error) {
    ElMessage.error('上传失败：' + error.message);
    uploading.value = false;
  }
};

const cancelUpload = () => {
  uploading.value = false;
  uploadStatus.value = '';
  currentFileProgress.value = 0;
  totalProgress.value = 0;
  uploadedFiles.value = 0;
  ElMessage.info('已取消上传');
};
</script>

<style scoped>
.upload-container {
  width: 100%;
  max-width: 600px;
  margin: 0 auto;
  padding: 20px;
}

.path-selector {
  margin-bottom: 20px;
}

.path-input {
  width: 100%;
}

.upload-component {
  width: 100%;
}

.upload-actions {
  margin-top: 20px;
  display: flex;
  gap: 10px;
  justify-content: center;
}

.upload-progress {
  margin-top: 20px;
  padding: 16px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
  color: #606266;
}

.total-progress {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.total-progress span {
  font-size: 14px;
  color: #606266;
}

.el-upload__tip {
  margin-top: 10px;
  color: #666;
}
</style>