import React from 'react';
import { FaChevronDown, FaChevronRight, FaFile, FaFolder } from 'react-icons/fa';
import '../FileExplorer.css';

const FileExplorer = ({ files, currentFile, onFileSelect, projectName }) => {
  const [expandedFolders, setExpandedFolders] = React.useState(new Set());

  const toggleFolder = (folderPath) => {
    const newExpanded = new Set(expandedFolders);
    if (newExpanded.has(folderPath)) {
      newExpanded.delete(folderPath);
    } else {
      newExpanded.add(folderPath);
    }
    setExpandedFolders(newExpanded);
  };

  // Build a tree structure from file names
  const buildTree = (files) => {
    const tree = {};
    
    Object.keys(files).forEach(fileName => {
      const parts = fileName.split('/');
      let current = tree;
      
      for (let i = 0; i < parts.length; i++) {
        const part = parts[i];
        const path = parts.slice(0, i + 1).join('/');
        
        if (i === parts.length - 1) {
          // It's a file
          if (!current.files) current.files = {};
          current.files[fileName] = true;
        } else {
          // It's a folder
          if (!current.folders) current.folders = {};
          if (!current.folders[part]) {
            current.folders[part] = { path };
          }
          current = current.folders[part];
        }
      }
    });
    
    return tree;
  };

  const renderTree = (tree, level = 0) => {
    const items = [];

    // Render folders
    if (tree.folders) {
      Object.entries(tree.folders).forEach(([folderName, folderData]) => {
        const isExpanded = expandedFolders.has(folderData.path);
        items.push(
          <div key={`folder-${folderData.path}`} className="file-tree-item">
            <div
              className="file-tree-node"
              style={{ paddingLeft: `${level * 15}px` }}
              onClick={() => toggleFolder(folderData.path)}
            >
              {isExpanded ? <FaChevronDown className="icon" /> : <FaChevronRight className="icon" />}
              <FaFolder className="folder-icon" />
              <span className="name">{folderName}</span>
            </div>
            {isExpanded && (
              <div className="folder-contents">
                {renderTree(folderData, level + 1)}
              </div>
            )}
          </div>
        );
      });
    }

    // Render files
    if (tree.files) {
      Object.keys(tree.files).forEach((fileName) => {
        const isSelected = currentFile === fileName;
        items.push(
          <div
            key={`file-${fileName}`}
            className={`file-tree-item file ${isSelected ? 'selected' : ''}`}
            style={{ paddingLeft: `${(level + 1) * 15}px` }}
            onClick={() => onFileSelect(fileName)}
          >
            <div className="file-tree-node">
              <FaFile className="file-icon" />
              <span className="name">{fileName.split('/').pop()}</span>
            </div>
          </div>
        );
      });
    }

    return items;
  };

  const tree = buildTree(files);
  const fileCount = Object.keys(files).length;

  return (
    <div className="file-explorer">
      <div className="explorer-header">
        <h3>{projectName}</h3>
        <span className="file-count">{fileCount} file{fileCount !== 1 ? 's' : ''}</span>
      </div>
      <div className="explorer-content">
        {fileCount === 0 ? (
          <div className="empty-state">No files</div>
        ) : (
          <div className="file-tree">
            {renderTree(tree)}
          </div>
        )}
      </div>
    </div>
  );
};

export default FileExplorer;
