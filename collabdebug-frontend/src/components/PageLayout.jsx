import React from "react";
import { useNavigate } from "react-router-dom";
import "../App.css";

const PageLayout = ({ title, children }) => {
  const navigate = useNavigate();
  return (
    <div className="container">
      <h1>{title}</h1>
      {children}
      <button className="back-btn" onClick={() => navigate(-1)}>
        Back
      </button>
    </div>
  );
};

export default PageLayout;
