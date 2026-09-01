import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const recoveryCaseApi = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

export const getRecoveryCases = async () => {
  const response = await recoveryCaseApi.get(
    "/recovery-cases"
  );

  return response.data;
};

export const getRecoveryCaseById = async (id) => {
  const response = await recoveryCaseApi.get(
    `/recovery-cases/${id}`
  );

  return response.data;
};

export const getRecoveryActions = async (id) => {
  const response = await recoveryCaseApi.get(
    `/recovery-cases/${id}/actions`
  );

  return response.data;
};

export default recoveryCaseApi;