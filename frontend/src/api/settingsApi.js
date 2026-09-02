import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const settingsApi = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

export const getSystemSettings = async () => {
  const response = await settingsApi.get("/settings");
  return response.data;
};

export default settingsApi;