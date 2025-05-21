<template>
  <el-container>
    <el-header>
      <h1>数据集文件管理系统</h1>
    </el-header>
    
    <el-main>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="文件上传" name="upload">
          <file-upload />
        </el-tab-pane>
        <el-tab-pane label="文件浏览" name="browse">
          <file-browser />
        </el-tab-pane>
      </el-tabs>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue';
import FileUpload from './components/FileUpload.vue';
import FileBrowser from './components/FileBrowser.vue';

const activeTab = ref('upload');
</script>

<style>
.el-header {
  background-color: #409EFF;
  color: white;
  text-align: center;
  line-height: 60px;
  padding: 0;
}

.el-main {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px);
}

h1 {
  margin: 0;
  font-size: 24px;
}
</style> 