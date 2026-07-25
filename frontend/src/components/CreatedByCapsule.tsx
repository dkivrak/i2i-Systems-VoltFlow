import { Zap, Linkedin, ExternalLink } from 'lucide-react';
import { useState } from 'react';

export interface CreatedByCapsuleProps {
  zelihaLink?: string;
  devrimLink?: string;
  onurLink?: string;
}

export function CreatedByCapsule({
  zelihaLink = 'https://www.linkedin.com/in/zeliha-ezer-75597840a/',
  devrimLink = 'https://www.linkedin.com/in/devrimkivrak/',
  onurLink = 'https://www.linkedin.com/in/onur-tezel-83a364249/',
}: CreatedByCapsuleProps) {
  const [isHovered, setIsHovered] = useState(false);

  const members = [
    { name: 'Zeliha Ezer', link: zelihaLink },
    { name: 'Devrim Mert Kıvrak', link: devrimLink },
    { name: 'Onur Tezel', link: onurLink },
  ];

  return (
    <div
      className={`created-by-capsule ${isHovered ? 'created-by-capsule--expanded' : ''}`}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onFocus={() => setIsHovered(true)}
      onBlur={() => setIsHovered(false)}
      tabIndex={0}
      role="region"
      aria-label="Created by overDOZ"
    >
      <div className="created-by-capsule__header">
        <span className="created-by-capsule__badge">
          <Zap size={14} className="created-by-capsule__zap" />
        </span>
        <span className="created-by-capsule__title">
          Created by <strong>overDOZ</strong>
        </span>
      </div>

      <div className="created-by-capsule__content">
        <div className="created-by-capsule__divider" />
        <ul className="created-by-capsule__list">
          {members.map((member) => (
            <li key={member.name} className="created-by-capsule__item">
              <a
                href={member.link}
                target="_blank"
                rel="noopener noreferrer"
                className="created-by-capsule__link"
                title={`${member.name} - LinkedIn Profilini Gör`}
              >
                <Linkedin size={14} className="created-by-capsule__icon" />
                <span className="created-by-capsule__name">{member.name}</span>
                <ExternalLink size={12} className="created-by-capsule__ext" />
              </a>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
