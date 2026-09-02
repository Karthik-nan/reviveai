import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const dashboardApi = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

export const getDashboardOverview = async () => {
  const response = await dashboardApi.get("/dashboard/overview");
  return response.data;
};

export const runRecoveryAnalysis = async () => {
  const response = await dashboardApi.post(
    "/dashboard/recovery-analysis"
  );

  return response.data;
};

export default dashboardApi;