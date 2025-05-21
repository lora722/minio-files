import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

export const fileApi = {
    // 上传文件
    uploadFile(file, path = '') {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('path', path);
        return axios.post(`${API_BASE_URL}/files/upload`, formData);
    },

    // 上传分片
    uploadChunk(chunk, uploadId, partNumber, path) {
        const formData = new FormData();
        formData.append('chunk', chunk);
        formData.append('uploadId', uploadId);
        formData.append('partNumber', partNumber);
        formData.append('path', path);
        return axios.post(`${API_BASE_URL}/files/upload/chunk`, formData);
    },

    // 完成上传
    completeUpload: async (formData) => {
        const response = await axios.post(`${API_BASE_URL}/files/upload/complete`, formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        });
        return response.data;
    },

    // 获取文件列表
    listFiles(path = '') {
        return axios.get(`${API_BASE_URL}/files/list`, {
            params: { path }
        });
    },

    // 下载文件
    downloadFile(path) {
        return axios.get(`${API_BASE_URL}/files/download`, {
            params: { path },
            responseType: 'blob'
        });
    },

    // 删除文件
    deleteFile(path) {
        return axios.delete(`${API_BASE_URL}/files`, {
            params: { path }
        });
    }
}; 